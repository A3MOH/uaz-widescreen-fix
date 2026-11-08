import argparse
import os
import shutil
import struct

# ChromeEngine2.dll, water object constructor: mov dword [esi+0x140], 256  (reflection render target size)
OFFSET = 258008
OPCODE = bytes.fromhex("c78640010000")
DEFAULT = 256


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    dll = os.path.join(os.path.dirname(os.path.dirname(here)), "ChromeEngine2.dll")
    ap = argparse.ArgumentParser(description="Set the water reflection texture size in ChromeEngine2.dll.")
    ap.add_argument("size", nargs="?", type=int, default=1024, help="power of two, 128..4096 (default 1024, game uses 256)")
    ap.add_argument("--restore", action="store_true", help="put the original dll back")
    args = ap.parse_args()

    backup = dll + ".orig"
    if args.restore:
        if os.path.exists(backup):
            shutil.copyfile(backup, dll)
            os.remove(backup)
            print("restored", dll)
        else:
            print("no backup")
        return

    if args.size < 128 or args.size > 4096 or args.size & (args.size - 1):
        raise SystemExit("size must be a power of two between 128 and 4096")
    data = bytearray(open(dll, "rb").read())
    if data[OFFSET:OFFSET + 6] != OPCODE:
        raise SystemExit("unexpected bytes at 0x%x, wrong dll version" % OFFSET)
    old = struct.unpack_from("<I", data, OFFSET + 6)[0]
    if not os.path.exists(backup):
        shutil.copyfile(dll, backup)
    struct.pack_into("<I", data, OFFSET + 6, args.size)
    open(dll, "wb").write(data)
    print("reflection size %d -> %d (backup %s)" % (old, args.size, backup))


if __name__ == "__main__":
    main()
