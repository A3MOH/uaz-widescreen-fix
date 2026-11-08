import argparse
import os
import struct

import numpy as np

SIZE = 256
SEED = 4242
COUNT = 900
R_MIN, R_MAX = 1.5, 5.5
EDGE = 0.9  # bead opacity at the rim: 1 = fully clear rim, 0 = hard edge


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


def beads(rng):
    height = np.zeros((SIZE, SIZE))
    shape = np.zeros((SIZE, SIZE))
    phase = np.zeros((SIZE, SIZE))
    y, x = np.mgrid[0:SIZE, 0:SIZE].astype(np.float64)
    for _ in range(COUNT):
        cx, cy = rng.uniform(0, SIZE, 2)
        r = R_MIN + (R_MAX - R_MIN) * rng.random() ** 2.5
        sx = r * rng.uniform(0.85, 1.15)
        dx = (x - cx + SIZE / 2) % SIZE - SIZE / 2
        dy = (y - cy + SIZE / 2) % SIZE - SIZE / 2
        d2 = (dx / sx) ** 2 + (dy / r) ** 2
        dome = np.sqrt(np.clip(1.0 - d2, 0.0, 1.0))
        cap = dome * r
        top = cap > height
        height[top] = cap[top]
        shape[top] = dome[top]
        phase[top] = rng.random()
    return height, shape, phase


def main():
    ap = argparse.ArgumentParser(description="Build tileable water beads for car paint (RG = normal xy, B = life phase, A = bead mask).")
    ap.add_argument("dst")
    ap.add_argument("--preview")
    args = ap.parse_args()
    h, shape, phase = beads(np.random.default_rng(SEED))
    gx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) * 0.5
    gy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) * 0.5
    n = np.stack([-gx, -gy, np.ones_like(h)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    mask = np.where(h > 0, 1.0 - EDGE * (1.0 - shape), 0.0)  # fades toward the rim
    # B = per-bead life phase (shader animates beads with it), G/R = normal xy, A = bead mask
    write_dds(args.dst, [phase, n[..., 1] * 0.5 + 0.5, n[..., 0] * 0.5 + 0.5, mask])
    print("%s: %dx%d, %.0f%% covered" % (args.dst, SIZE, SIZE, (h > 0).mean() * 100))
    if args.preview:
        from PIL import Image
        Image.fromarray(np.clip(np.concatenate([(n * 0.5 + 0.5), np.repeat(mask[..., None], 3, -1)], 1) * 255,
                                0, 255).astype(np.uint8)).save(args.preview)


if __name__ == "__main__":
    main()
