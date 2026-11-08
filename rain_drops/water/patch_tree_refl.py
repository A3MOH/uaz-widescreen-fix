import argparse
import os
import shutil
import struct

# ChromeEngine2.dll, CTrees::LoadBilboards: 4th material slot (reflected tree billboards)
# loads "billboards_blended.mat". Point it at the tail of that string, "_blended.mat",
# so reflected trees get their own material: Data\nature\_blended.mat
PUSH = 312590
OLD = 0x1033841C
NEW = 0x10338426


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    dll = os.path.join(os.path.dirname(os.path.dirname(here)), "ChromeEngine2.dll")
    ap = argparse.ArgumentParser(description="Give reflected tree billboards their own material (_blended.mat).")
    ap.add_argument("--undo", action="store_true", help="point the slot back to billboards_blended.mat")
    args = ap.parse_args()

    data = bytearray(open(dll, "rb").read())
    if data[PUSH] != 0x68:
        raise SystemExit("unexpected bytes at 0x%x, wrong dll version" % PUSH)
    cur = struct.unpack_from("<I", data, PUSH + 1)[0]
    want = OLD if args.undo else NEW
    if cur not in (OLD, NEW):
        raise SystemExit("unexpected pointer 0x%x, wrong dll version" % cur)
    if cur == want:
        print("already set")
        return
    if not os.path.exists(dll + ".orig"):
        shutil.copyfile(dll, dll + ".orig")
    struct.pack_into("<I", data, PUSH + 1, want)
    open(dll, "wb").write(data)
    print("reflected tree material ->", "billboards_blended.mat" if args.undo else "_blended.mat")


if __name__ == "__main__":
    main()
