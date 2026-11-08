import argparse
import struct

import numpy as np

SIZE = 256
DROPS = 70
R_MIN = 9.0
R_MAX = 16.0
SEED = 4242


def build(size, drops, rng):
    mask = np.zeros((size, size))
    dx = np.zeros((size, size))
    dy = np.zeros((size, size))
    tm = np.zeros((size, size))
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float64)
    placed = []
    tries = 0
    while len(placed) < drops and tries < drops * 200:
        tries += 1
        cx, cy = rng.uniform(0, size, 2)
        r = rng.uniform(R_MIN, R_MAX)
        ok = True
        for (px, py, pr) in placed:
            ddx = abs(cx - px)
            ddy = abs(cy - py)
            ddx = min(ddx, size - ddx)
            ddy = min(ddy, size - ddy)
            if ddx * ddx + ddy * ddy < (r + pr) ** 2:
                ok = False
                break
        if not ok:
            continue
        placed.append((cx, cy, r))
        ox = (xx - cx + size / 2) % size - size / 2
        oy = (yy - cy + size / 2) % size - size / 2
        d = np.sqrt(ox * ox + oy * oy)
        inside = d < r
        m = np.clip(1.0 - d / r, 0.0, 1.0)
        sel = inside & (m > mask)
        inv = np.where(d > 0, 1.0 / np.maximum(d, 1e-6), 0.0)
        mask[sel] = m[sel]
        dx[sel] = (ox * inv)[sel]
        dy[sel] = (oy * inv)[sel]
        tm[sel] = rng.uniform(0.0, 1.0)
    return mask, dx, dy, tm, len(placed)


def pack(mask, dx, dy, tm):
    r = np.clip(np.rint(mask * 255), 0, 255).astype(np.uint8)
    g = np.clip(np.rint((dx * 0.5 + 0.5) * 255), 0, 255).astype(np.uint8)
    b = np.clip(np.rint((-dy * 0.5 + 0.5) * 255), 0, 255).astype(np.uint8)
    a = np.clip(np.rint(tm * 255), 0, 255).astype(np.uint8)
    return np.stack([b, g, r, a], -1).tobytes()


def write_dds(path, chans):
    mask, dx, dy, tm = chans
    h, w = mask.shape
    levels = []
    while True:
        levels.append(pack(mask, dx, dy, tm))
        if mask.shape[0] == 1:
            break
        mask, dx, dy, tm = [c[0::2, 0::2] for c in (mask, dx, dy, tm)]
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
    ap = argparse.ArgumentParser(description="Build tileable rain ripple texture (R mask, G/B ring dir, A time offset).")
    ap.add_argument("dst")
    ap.add_argument("--preview")
    args = ap.parse_args()
    rng = np.random.default_rng(SEED)
    mask, dx, dy, tm, n = build(SIZE, DROPS, rng)
    mips = write_dds(args.dst, (mask, dx, dy, tm))
    print("%s: %dx%d, %d drops, %d mips" % (args.dst, SIZE, SIZE, n, mips))
    if args.preview:
        from PIL import Image
        img = np.concatenate([mask, dx * 0.5 + 0.5, tm * (mask > 0)], 1)
        Image.fromarray((img * 255).astype(np.uint8)).save(args.preview)


if __name__ == "__main__":
    main()
