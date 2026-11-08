import argparse
import glob
import os
import re

NUM = r"\s*(-?[0-9.]+)\s*"
CHANNELS = ("r", "g", "b", "a")


def find_var(block, name):
    m = re.search(r'VarFloat\(\s*"%s"\s*,%s\)' % (name, NUM), block)
    return m.group(1) if m else None


def state_vars(block):
    out = {}
    for base in ("skycolor", "daycolor", "ambient"):
        vals = [find_var(block, "%s_%s" % (base, c)) for c in CHANNELS]
        if None in vals:
            return None
        out[base] = vals
    out["daycolor"][3] = "0.00"
    m = re.search(r'VarFog\(\s*"fogcolor_r"\s*,%s,\s*"fogcolor_g"\s*,%s,\s*"fogcolor_b"\s*,%s\)' % (NUM, NUM, NUM), block)
    far = re.search(r"VarFogFar\(%s\)" % NUM, block)
    if not m or not far:
        return None
    out["fogcolor"] = [m.group(1), m.group(2), m.group(3), far.group(1)]
    return out


def cubemap_lines(v, indent, nl):
    lines = []
    for base in ("skycolor", "daycolor", "fogcolor", "ambient"):
        for c, val in zip(CHANNELS, v[base]):
            lines.append('%sVarFloat("dyncubemap_%s_%s", %s)' % (indent, base, c, val))
    return nl.join(lines)


def state_blocks(text):
    for m in re.finditer(r'State\(\s*"([^"]+)"\s*\)\s*\{', text):
        depth, i = 1, m.end()
        while depth and i < len(text):
            depth += {"{": 1, "}": -1}.get(text[i], 0)
            i += 1
        yield m.group(1), m.end(), i


def patch(text):
    nl = "\r\n" if "\r\n" in text else "\n"
    out, last, count = [], 0, 0
    for name, start, end in state_blocks(text):
        block = text[start:end]
        inc = re.search(r'^([ \t]*)!include\("[^"]*DynCubemap\.clm"\)[ \t]*(?=\r?$)', block, re.M | re.I)
        v = state_vars(block)
        if not inc or not v:
            continue
        out.append(text[last:start + inc.start()])
        out.append(cubemap_lines(v, inc.group(1), nl))
        last = start + inc.end()
        count += 1
    out.append(text[last:])
    return "".join(out), count


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    data = os.path.join(os.path.dirname(here), "Data")
    ap = argparse.ArgumentParser(description="Make car reflections follow the weather: give each climate state its own dyncubemap colors.")
    ap.add_argument("--out", default=os.path.join(here, "weather_cubemap"))
    args = ap.parse_args()

    manifest = []
    os.makedirs(args.out, exist_ok=True)
    for clm in sorted(glob.glob(os.path.join(data, "mapscustom", "*", "*.clm"))):
        if "dyncubemap" in os.path.basename(clm).lower():
            continue
        src = clm + ".orig" if os.path.exists(clm + ".orig") else clm
        text = open(src, "rb").read().decode("latin-1")
        new, count = patch(text)
        if not count:
            continue
        lvl = os.path.basename(os.path.dirname(clm))
        os.makedirs(os.path.join(args.out, lvl), exist_ok=True)
        open(os.path.join(args.out, lvl, os.path.basename(clm)), "wb").write(new.encode("latin-1"))
        manifest.append("%s|%s" % (lvl, os.path.basename(clm)))
        print("%s: %d states" % (os.path.relpath(clm, data), count))

    with open(os.path.join(args.out, "list.txt"), "w") as f:
        f.write("\n".join(manifest) + "\n")


if __name__ == "__main__":
    main()
