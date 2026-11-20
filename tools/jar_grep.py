#!/usr/bin/env python3
"""Inspect a jar's constant pools without javap.

Used during the port to answer questions like "which GL class declares GL_BLEND_COLOR"
cheaply and without pulling a thousand lines of javap output through a shell pipeline.
"""

import struct
import sys
import zipfile


CONSTANT_SIZES = {
    3: 4,    # Integer
    4: 4,    # Float
    5: 8,    # Long
    6: 8,    # Double
    7: 2,    # Class
    8: 2,    # String
    9: 4,    # Fieldref
    10: 4,   # Methodref
    11: 4,   # InterfaceMethodref
    12: 4,   # NameAndType
    15: 3,   # MethodHandle
    16: 2,   # MethodType
    17: 4,   # Dynamic
    18: 4,   # InvokeDynamic
    19: 2,   # Module
    20: 2,   # Package
}


def constant_pool_strings(data):
    """Return every UTF-8 constant in a class file."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return []
    count = struct.unpack_from(">H", data, 8)[0]
    offset = 10
    strings = []
    index = 1
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            strings.append(data[offset:offset + length].decode("utf-8", "replace"))
            offset += length
        else:
            size = CONSTANT_SIZES.get(tag)
            if size is None:
                break
            offset += size
        index += 1
        # Long and Double occupy two constant pool slots.
        if tag in (5, 6):
            index += 1
    return strings


def main():
    if len(sys.argv) < 3:
        print("usage: jar_grep.py <jar> <substring> [entry-prefix]")
        return 2
    jar_path, needle = sys.argv[1], sys.argv[2]
    prefix = sys.argv[3] if len(sys.argv) > 3 else ""
    hits = []
    with zipfile.ZipFile(jar_path) as jar:
        for name in jar.namelist():
            if not name.endswith(".class") or not name.startswith(prefix):
                continue
            for constant in constant_pool_strings(jar.read(name)):
                if needle in constant:
                    hits.append((name, constant))
    for name, constant in hits:
        print(f"{name}\t{constant}")
    print(f"-- {len(hits)} hits in {jar_path}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
