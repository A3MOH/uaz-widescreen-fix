import argparse
import os
import shutil
import struct
import sys

import numpy as np

BACKUP_EXT = ".orig"


def read_height(path):
    with open(path, "rb") as f:
        data = f.read()
    w, h = struct.unpack_from("<2i", data, 0)
    if len(data) != 8 + w * h * 4:
        raise ValueError("%s: size mismatch (%dx%d, %d bytes)" % (path, w, h, len(data)))
    grid = np.frombuffer(data, dtype="<f4", count=w * h, offset=8).reshape(h, w).astype(np.float64)
    return grid


def write_height(path, grid):
    h, w = grid.shape
    with open(path, "wb") as f:
        f.write(struct.pack("<2i", w, h))
        f.write(grid.astype("<f4").tobytes())


def cubic_weights(t):
    t2 = t * t
    t3 = t2 * t
    return (
        -0.5 * t3 + t2 - 0.5 * t,
        1.5 * t3 - 2.5 * t2 + 1.0,
        -1.5 * t3 + 2.0 * t2 + 0.5 * t,
        0.5 * t3 - 0.5 * t2,
    )


def upsample_axis(a, factor, axis):
    a = np.moveaxis(a, axis, 0)
    n = a.shape[0]
    m = (n - 1) * factor + 1
    pos = np.arange(m, dtype=np.float64) / factor
    i = np.minimum(np.floor(pos).astype(np.int64), n - 2)
    t = pos - i
    w0, w1, w2, w3 = cubic_weights(t)
    i0 = np.clip(i - 1, 0, n - 1)
    i1 = i
    i2 = np.clip(i + 1, 0, n - 1)
    i3 = np.clip(i + 2, 0, n - 1)
    shape = (m,) + (1,) * (a.ndim - 1)
    out = (a[i0] * w0.reshape(shape) + a[i1] * w1.reshape(shape) +
           a[i2] * w2.reshape(shape) + a[i3] * w3.reshape(shape))
    return np.moveaxis(out, 0, axis)


def upsample(grid, factor):
    lo = grid.min()
    hi = grid.max()
    out = upsample_axis(grid, factor, 1)
    out = upsample_axis(out, factor, 0)
    return np.clip(out, lo, hi)


def main():
    ap = argparse.ArgumentParser(description="Upsample UAZ 4x4 .height terrain files (Catmull-Rom).")
    ap.add_argument("files", nargs="+", help=".height files to process")
    ap.add_argument("--factor", type=int, default=2, help="grid density multiplier (default 2)")
    ap.add_argument("--max-side", type=int, default=2049,
                    help="refuse results larger than this (default 2049; 4097 crashes the game)")
    ap.add_argument("--restore", action="store_true", help="restore originals from .orig backups")
    args = ap.parse_args()

    for path in args.files:
        backup = path + BACKUP_EXT
        if args.restore:
            if os.path.exists(backup):
                shutil.copyfile(backup, path)
                os.remove(backup)
                print("restored", path)
            else:
                print("no backup for", path)
            continue

        if not os.path.exists(backup):
            shutil.copyfile(path, backup)
        grid = read_height(backup)
        h, w = grid.shape
        nw = (w - 1) * args.factor + 1
        nh = (h - 1) * args.factor + 1
        if max(nw, nh) > args.max_side:
            print("skip %s: %dx%d would exceed %d" % (path, nw, nh, args.max_side))
            continue
        out = upsample(grid, args.factor)
        if not np.array_equal(out[::args.factor, ::args.factor].astype(np.float32), grid.astype(np.float32)):
            print("error %s: original samples changed" % path)
            sys.exit(1)
        write_height(path, out)
        print("%s: %dx%d -> %dx%d (backup %s)" % (path, w, h, nw, nh, backup))


if __name__ == "__main__":
    main()
