import argparse
import struct

import numpy as np

SIZE = 256
SEED = 7331


def tile_noise(rng, size, falloff):
    f = np.fft.fftfreq(size)
    k = np.sqrt(f[None, :] ** 2 + f[:, None] ** 2)
    k[0, 0] = 1.0
    spec = (rng.standard_normal((size, size)) + 1j * rng.standard_normal((size, size))) / k ** falloff
    spec[0, 0] = 0.0
    n = np.real(np.fft.ifft2(spec))
    n -= n.min()
    return n / n.max()


def equalize(n):
    order = np.argsort(n, axis=None)
    out = np.empty(n.size)
    out[order] = np.linspace(0.0, 1.0, n.size)
    return out.reshape(n.shape)


def write_dds(path, chans):
    h, w = chans[0].shape
    levels = []
    cur = [c.copy() for c in chans]
    while True:
        b, g, r, a = [np.clip(np.rint(c * 255.0), 0, 255).astype(np.uint8) for c in cur]
        levels.append(np.stack([b, g, r, a], -1).tobytes())
        if cur[0].shape[0] == 1:
            break
        cur = [(c[0::2, 0::2] + c[1::2, 0::2] + c[0::2, 1::2] + c[1::2, 1::2]) * 0.25 for c in cur]
    header = struct.pack(
        "<4s7I44x2I4s5I5I",
        b"DDS ", 124, 0x1 | 0x2 | 0x4 | 0x1000 | 0x8 | 0x20000, h, w, w * 4, 0, len(levels),
        32, 0x41, b"\0\0\0\0", 32, 0xff0000, 0xff00, 0xff, 0xff000000,
        0x1000 | 0x8 | 0x400000, 0, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(header)
        for lv in levels:
            f.write(lv)
    return len(levels)


def main():
    ap = argparse.ArgumentParser(description="Build tileable puddle noise (B,G,R = blobs, detail, ripple; A = blobs).")
    ap.add_argument("dst")
    ap.add_argument("--preview")
    args = ap.parse_args()
    rng = np.random.default_rng(SEED)
    blobs = equalize(tile_noise(rng, SIZE, 1.6))
    detail = equalize(tile_noise(rng, SIZE, 1.1))
    ripple = tile_noise(rng, SIZE, 0.6)
    mips = write_dds(args.dst, [blobs, detail, ripple, blobs])
    print("%s: %dx%d, %d mips" % (args.dst, SIZE, SIZE, mips))
    if args.preview:
        from PIL import Image
        Image.fromarray(np.concatenate([blobs, detail, ripple], 1).__mul__(255).astype(np.uint8)).save(args.preview)


if __name__ == "__main__":
    main()
