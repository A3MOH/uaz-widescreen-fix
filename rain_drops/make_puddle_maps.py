import argparse
import glob
import heapq
import os
import re
import struct

import numpy as np

TEX = 2048
NOISE_TILES = 4
SHORE_RANGE = 200.0
CAV_TEX = 1024
DEFAULT_CAVITY = 0.3

SMALL_R, SMALL_DEPTH = 2, 0.10
LARGE_R, LARGE_DEPTH = 6, 0.40
FILL_DEPTH = 0.30
FILL_MAX, FILL_FADE = 3.0, 1.5
SHARPNESS = 3


def read_noise(path):
    d = open(path, "rb").read()
    h, w = struct.unpack_from("<2I", d, 12)
    px = np.frombuffer(d, np.uint8, w * h * 4, 128).reshape(h, w, 4).astype(np.float64) / 255.0
    return [px[..., i] for i in range(4)]


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


def read_height(path):
    d = open(path, "rb").read()
    w, h = struct.unpack_from("<2i", d, 0)
    if len(d) != 8 + w * h * 4:
        raise ValueError("%s: size mismatch" % path)
    return np.frombuffer(d, "<f4", w * h, 8).reshape(h, w).astype(np.float64)


def read_map(game, path):
    d = open(path, "rb").read()
    i = d.lower().find(b".height")
    if i < 0:
        raise ValueError("%s: no heightmap" % path)
    s = d.rfind(b".\\", 0, i)
    rel = d[s + 2:i + 7].decode("latin-1").replace("\\", os.sep)
    size_x, scale_y, size_z = struct.unpack_from("<3f", d, i + 9)
    m = re.search(rb"Data.World.Water[A-Za-z]*\.mat", d, re.I)
    water = struct.unpack_from("<f", d, m.start() - 44)[0] if m else None
    return os.path.join(game, rel), size_x, scale_y, size_z, water


def cubic_weights(t):
    t2 = t * t
    t3 = t2 * t
    return (-0.5 * t3 + t2 - 0.5 * t, 1.5 * t3 - 2.5 * t2 + 1.0,
            -1.5 * t3 + 2.0 * t2 + 0.5 * t, 0.5 * t3 - 0.5 * t2)


def resample_axis(a, size, axis):
    a = np.moveaxis(a, axis, 0)
    n = a.shape[0]
    pos = (np.arange(size) + 0.5) / size * (n - 1)
    i = np.minimum(np.floor(pos).astype(np.int64), n - 2)
    w = cubic_weights(pos - i)
    shape = (size,) + (1,) * (a.ndim - 1)
    out = sum(a[np.clip(i + k - 1, 0, n - 1)] * w[k].reshape(shape) for k in range(4))
    return np.moveaxis(out, 0, axis)


def smooth_height(grid, size):
    return resample_axis(resample_axis(grid, size, 1), size, 0)


def upsample_wrap(a, f):
    n = a.shape[0]
    pos = (np.arange(n * f) + 0.5) / f - 0.5
    i = np.floor(pos).astype(np.int64)
    t = pos - i
    i0, i1 = i % n, (i + 1) % n
    a = a[i0] * (1 - t)[:, None] + a[i1] * t[:, None]
    return a[:, i0] * (1 - t)[None, :] + a[:, i1] * t[None, :]


def box_blur(a, r):
    if r <= 0:
        return a
    n = 2 * r + 1
    for axis in (0, 1):
        p = np.pad(a, [(r + 1, r) if k == axis else (0, 0) for k in (0, 1)], mode="edge")
        c = np.cumsum(p, axis=axis)
        hi = np.take(c, range(n, c.shape[axis]), axis=axis)
        lo = np.take(c, range(0, c.shape[axis] - n), axis=axis)
        a = (hi - lo) / n
    return a


def smooth(a, r):
    for _ in range(3):
        a = box_blur(a, r)
    return a


def priority_flood(z):
    h, w = z.shape
    flat = z.ravel()
    filled = flat.copy()
    done = np.zeros(h * w, bool)
    heap = []
    for idx in np.concatenate([np.arange(w), np.arange((h - 1) * w, h * w),
                               np.arange(w, (h - 1) * w, w), np.arange(2 * w - 1, (h - 1) * w, w)]):
        idx = int(idx)
        if not done[idx]:
            done[idx] = True
            heap.append((flat[idx], idx))
    heapq.heapify(heap)
    push, pop = heapq.heappush, heapq.heappop
    while heap:
        v, idx = pop(heap)
        y, x = divmod(idx, w)
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < h and 0 <= nx < w:
                n = ny * w + nx
                if not done[n]:
                    done[n] = True
                    nv = flat[n] if flat[n] > v else v
                    filled[n] = nv
                    push(heap, (nv, n))
    return filled.reshape(h, w)


def cavity(grid_m, cell_m):
    sat = lambda a: np.clip(a, 0.0, 1.0)
    small = smooth(grid_m, max(1, int(round(SMALL_R / cell_m)))) - grid_m
    large = smooth(grid_m, max(1, int(round(LARGE_R / cell_m)))) - grid_m
    fill = priority_flood(grid_m) - grid_m
    score = small / SMALL_DEPTH + large / LARGE_DEPTH
    score += np.minimum(fill, FILL_MAX) / FILL_DEPTH * sat((FILL_MAX + FILL_FADE - fill) / FILL_FADE)
    score = smooth(score, 1)
    rank = np.empty(score.size)
    rank[np.argsort(score, axis=None, kind="stable")] = np.linspace(0.0, 1.0, score.size)
    return rank.reshape(score.shape) ** SHARPNESS


def to_texture(grid):
    n = grid.shape[0] - 1
    step = max(1, n // CAV_TEX)
    g = grid[::step, ::step] if step > 1 else grid
    g = (g[:-1, :-1] + g[1:, :-1] + g[:-1, 1:] + g[1:, 1:]) * 0.25
    while g.shape[0] < TEX:
        g = np.clip(upsample_clamp(g), 0.0, 1.0)
    return g


def upsample_clamp(a):
    p = np.pad(a, 1, mode="edge")
    c = p[1:-1, 1:-1]
    out = np.empty((a.shape[0] * 2, a.shape[1] * 2))
    for dy, ys in ((0, slice(0, -2)), (1, slice(2, None))):
        for dx, xs in ((0, slice(0, -2)), (1, slice(2, None))):
            ny = p[ys, 1:-1]
            nx = p[1:-1, xs]
            nd = p[ys, xs]
            out[dy::2, dx::2] = c * 0.5625 + ny * 0.1875 + nx * 0.1875 + nd * 0.0625
    return out


def find_levels(data):
    levels = []
    for info in sorted(glob.glob(os.path.join(data, "mapscustom", "*", "info.scr"))):
        folder = os.path.dirname(info)
        m = re.search(r'Race\(\s*"[^"]*"\s*,\s*"([^"]+\.map)"', open(info, encoding="latin-1").read(), re.I)
        if not m:
            continue
        scr, stem = None, None
        for f in glob.glob(os.path.join(folder, "*.scr")):
            mm = re.search(r'VarString\(\s*"colortexture"\s*,\s*"TerrainColor_([^"]+)\.dds"',
                           open(f, encoding="latin-1").read(), re.I)
            if mm:
                scr, stem = f, mm.group(1)
                break
        if scr:
            levels.append((folder, m.group(1), scr, stem))
    return levels


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    game = os.path.dirname(here)
    ap = argparse.ArgumentParser(description="Bake per-level puddle maps (R = terrain dips, B = shore height, G/A = puddle noise).")
    ap.add_argument("--out", default=os.path.join(here, "puddle_maps"))
    ap.add_argument("--noise", default=os.path.join(here, "PuddleNoise.dds"))
    ap.add_argument("--preview", help="folder for PNG previews of the dip mask")
    ap.add_argument("levels", nargs="*", help="level folder names (default: all)")
    args = ap.parse_args()

    tile = read_noise(args.noise)
    f = TEX // NOISE_TILES // tile[0].shape[0]
    if f < 1 or tile[0].shape[0] * f * NOISE_TILES != TEX:
        raise ValueError("noise tile size does not fit %d px" % TEX)
    noise = [np.tile(upsample_wrap(c, f) if f > 1 else c, (NOISE_TILES, NOISE_TILES)) for c in tile]
    os.makedirs(args.out, exist_ok=True)

    write_dds(os.path.join(args.out, "PuddleMap_P.dds"),
              [np.ones((TEX, TEX)), noise[1], np.full((TEX, TEX), DEFAULT_CAVITY), noise[3]])
    print("default: PuddleMap_P.dds")

    manifest = []
    for folder, map_rel, scr, stem in find_levels(os.path.join(game, "Data")):
        name = os.path.basename(folder)
        if args.levels and name.lower() not in [l.lower() for l in args.levels]:
            continue
        hpath, size_x, scale_y, size_z, water = read_map(game, os.path.join(game, map_rel.replace("/", os.sep)))
        src = hpath + ".orig" if os.path.exists(hpath + ".orig") else hpath
        raw = read_height(src)
        n = raw.shape[0] - 1
        step = max(1, n // CAV_TEX)
        grid_m = raw[::step, ::step] / 255.0 * scale_y / 100.0
        cell_m = size_x / 100.0 / (grid_m.shape[0] - 1)
        cav = to_texture(cavity(grid_m, cell_m))
        dds = "Puddle_%s.dds" % stem
        shore = np.ones((TEX, TEX))
        extra = ""
        if water is not None:
            mesh = read_height(hpath) / 255.0 * scale_y
            hs = smooth_height(mesh, TEX)
            shore = np.clip((hs - water) / (2 * SHORE_RANGE) + 0.5, 0.0, 1.0)
            extra = "|%g|%g" % (water, SHORE_RANGE)
        write_dds(os.path.join(args.out, dds), [shore, noise[1], cav, noise[3]])
        manifest.append("%s|%s|%s%s" % (name, os.path.basename(scr), dds, extra))
        print("%s: %s %dx%d, cell %.2fm, dips %.0f%%, water %s -> %s" % (
            name, os.path.basename(src), raw.shape[1], raw.shape[0], cell_m, (cav > 0.5).mean() * 100, water, dds))
        if args.preview:
            from PIL import Image
            os.makedirs(args.preview, exist_ok=True)
            Image.fromarray((cav * 255).astype(np.uint8)).save(os.path.join(args.preview, "%s.png" % stem))

    if not args.levels:
        with open(os.path.join(args.out, "maps.txt"), "w") as f:
            f.write("\n".join(manifest) + "\n")


if __name__ == "__main__":
    main()
