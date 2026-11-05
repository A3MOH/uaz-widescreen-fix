import argparse
import struct

import numpy as np

FRAMES_X = 2
FRAMES_Y = 2
BUMP = 0.35
BLUR = 2
THRESHOLD = 0.08
DOME_RADIUS = 22


def read_dds_argb(path):
    with open(path, "rb") as f:
        d = f.read()
    if d[:4] != b"DDS ":
        raise ValueError("not a DDS file")
    h, w = struct.unpack_from("<2I", d, 12)
    bits = struct.unpack_from("<I", d, 88)[0]
    masks = struct.unpack_from("<4I", d, 92)
    if bits != 32 or masks != (0xff0000, 0xff00, 0xff, 0xff000000):
        raise ValueError("expected uncompressed A8R8G8B8")
    a = np.frombuffer(d, np.uint8, w * h * 4, 128).reshape(h, w, 4)
    return a[..., 3].astype(np.float64) / 255.0


def blur(img, r):
    if r <= 0:
        return img
    k = np.exp(-0.5 * (np.arange(-2 * r, 2 * r + 1) / float(r)) ** 2)
    k /= k.sum()
    p = np.pad(img, 2 * r, mode="edge")
    tmp = np.zeros_like(p)
    for i, kv in enumerate(k):
        tmp += kv * np.roll(p, i - 2 * r, axis=1)
    out = np.zeros_like(p)
    for i, kv in enumerate(k):
        out += kv * np.roll(tmp, i - 2 * r, axis=0)
    return out[2 * r:-2 * r, 2 * r:-2 * r]


def shift(b, dy, dx):
    out = np.zeros_like(b)
    h, w = b.shape
    out[max(dy, 0):h + min(dy, 0), max(dx, 0):w + min(dx, 0)] = \
        b[max(-dy, 0):h + min(-dy, 0), max(-dx, 0):w + min(-dx, 0)]
    return out


def dilate(b):
    return b | shift(b, 1, 0) | shift(b, -1, 0) | shift(b, 0, 1) | shift(b, 0, -1)


def erode(b):
    return ~dilate(~b)


def fill_holes(solid):
    bg = ~solid
    reach = np.zeros_like(solid)
    reach[0, :] = bg[0, :]
    reach[-1, :] = bg[-1, :]
    reach[:, 0] = bg[:, 0]
    reach[:, -1] = bg[:, -1]
    while True:
        nxt = dilate(reach) & bg
        if (nxt == reach).all():
            return ~reach
        reach = nxt


def frame_normals(alpha):
    solid = fill_holes(alpha > THRESHOLD)
    dist = np.zeros(alpha.shape)
    cur = solid.copy()
    for _ in range(DOME_RADIUS):
        dist += cur
        cur = erode(cur)
    t = dist / DOME_RADIUS
    height = blur(np.sqrt(np.clip(1.0 - (1.0 - t) ** 2, 0.0, 1.0)) * DOME_RADIUS, BLUR)
    gy, gx = np.gradient(height)
    nx = -gx * BUMP
    nup = gy * BUMP
    nz = np.ones_like(nx)
    ln = np.sqrt(nx * nx + nup * nup + nz * nz)
    mask = blur(solid.astype(np.float64), 1)
    return np.stack([nx / ln, nup / ln, nz / ln], -1), mask


def build(alpha):
    h, w = alpha.shape
    fh, fw = h // FRAMES_Y, w // FRAMES_X
    n = np.zeros((h, w, 3))
    m = np.zeros((h, w))
    for fy in range(FRAMES_Y):
        for fx in range(FRAMES_X):
            ys = slice(fy * fh, (fy + 1) * fh)
            xs = slice(fx * fw, (fx + 1) * fw)
            n[ys, xs], m[ys, xs] = frame_normals(alpha[ys, xs])
    return n, m


def downsample(n, m):
    h, w = m.shape
    if h > 1:
        n = (n[0::2] + n[1::2]) * 0.5
        m = (m[0::2] + m[1::2]) * 0.5
    if w > 1:
        n = (n[:, 0::2] + n[:, 1::2]) * 0.5
        m = (m[:, 0::2] + m[:, 1::2]) * 0.5
    ln = np.sqrt((n * n).sum(-1, keepdims=True))
    ln[ln == 0] = 1.0
    return n / ln, m


def pack(n, m):
    c = np.clip(np.rint((n * 0.5 + 0.5) * 255.0), 0, 255).astype(np.uint8)
    a = np.clip(np.rint(m * 255.0), 0, 255).astype(np.uint8)
    bgra = np.stack([c[..., 2], c[..., 1], c[..., 0], a], -1)
    return bgra.tobytes()


def write_dds(path, n, m):
    h, w = m.shape
    levels = []
    while True:
        levels.append(pack(n, m))
        if n.shape[0] == 1 and n.shape[1] == 1:
            break
        n, m = downsample(n, m)
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
    ap = argparse.ArgumentParser(description="Build rain drop normal map from OnFrontGlass_Rain.dds alpha.")
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--preview")
    args = ap.parse_args()
    alpha = read_dds_argb(args.src)
    n, m = build(alpha)
    mips = write_dds(args.dst, n, m)
    print("%s: %dx%d, %d mips" % (args.dst, m.shape[1], m.shape[0], mips))
    if args.preview:
        from PIL import Image
        rgb = np.clip(np.rint((n * 0.5 + 0.5) * 255.0), 0, 255).astype(np.uint8)
        Image.fromarray(np.concatenate([rgb, np.repeat((m * 255).astype(np.uint8)[..., None], 3, -1)], 1)).save(args.preview)


if __name__ == "__main__":
    main()
