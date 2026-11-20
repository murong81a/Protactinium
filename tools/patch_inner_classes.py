#!/usr/bin/env python3
"""Patch the InnerClasses attribute so nested classes read as public to javac.

Why this is needed and why re-writing the class file's own access flags is not enough:

Java source that says `Foo.Bar` where `Bar` is a nested class is resolved by javac through the
InnerClasses attribute of the *enclosing* class, not through the access flags in `Bar.class`.
The attribute carries its own `inner_class_access_flags` copy, and javac trusts that copy. An
access transformer that only widens `Bar.class` therefore still leaves `Foo.Bar` inaccessible to
the compiler: the enclosing class keeps advertising `static` with no `public` bit.

This is exactly the gap the AT tool left behind, and it is invisible in javap's normal output
because `javap -p` prints the class-file flags, which do say `public`.

The fix is to widen the same nested classes inside every enclosing class's InnerClasses attribute.
Attribution is by the `inner_class_info_index` entry, so a class that names several nested entries
is patched entry by entry and everything else in the attribute is preserved byte for byte.

Usage: patch_inner_classes.py <jar> <at-file> <output.jar>
"""

import struct
import sys
import zipfile

ACC_PUBLIC = 0x0001
ACC_PRIVATE = 0x0002
ACC_PROTECTED = 0x0004
ACC_FINAL = 0x0010

CLEAR_ACCESS = ~(ACC_PRIVATE | ACC_PROTECTED | ACC_PUBLIC) & 0xFFFF

ACCESS = {
    "public": (0, ACC_PUBLIC),
    "public-f": (ACC_FINAL, ACC_PUBLIC),
    "protected": (0, ACC_PROTECTED),
    "protected-f": (ACC_FINAL, ACC_PROTECTED),
    "private": (0, ACC_PRIVATE),
    "private-f": (ACC_FINAL, ACC_PRIVATE),
}


def parse_at_targets(path):
    """Return {internal class name: (clear_final, set_bits)} for nested classes only."""
    targets = {}
    with open(path, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) < 2:
                continue
            access = parts[0]
            if access not in ACCESS:
                continue
            name = parts[1].replace(".", "/")
            if "$" not in name:
                continue
            clear_final, sets = ACCESS[access]
            targets[name] = (clear_final, sets)
    return targets


class Pool:
    """Constant pool reader that records the byte offset of every entry."""

    def __init__(self, data):
        self.data = data
        self.offsets = {}
        count = struct.unpack_from(">H", data, 8)[0]
        offset = 10
        index = 1
        while index < count:
            self.offsets[index] = offset
            tag = data[offset]
            offset += 1
            if tag == 1:
                length = struct.unpack_from(">H", data, offset)[0]
                offset += 2 + length
            elif tag in (7, 8, 16, 19, 20):
                offset += 2
            elif tag == 15:
                offset += 3
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                offset += 4
            elif tag in (5, 6):
                offset += 8
            else:
                raise ValueError(f"constant pool tag {tag}")
            index += 1
            if tag in (5, 6):
                index += 1
        self.end = offset

    def utf8(self, index):
        offset = self.offsets[index] + 1
        length = struct.unpack_from(">H", self.data, offset)[0]
        return self.data[offset + 2:offset + 2 + length].decode("utf-8", "replace")

    def class_name(self, index):
        """Class entry -> its internal name."""
        offset = self.offsets[index]
        name_index = struct.unpack_from(">H", self.data, offset + 1)[0]
        return self.utf8(name_index)


def patch_inner_classes(data, targets):
    """Return (new bytes, number of entries patched)."""
    mutable = bytearray(data)
    pool = Pool(mutable)

    offset = pool.end + 2          # access_flags
    offset += 4                    # this_class, super_class
    interfaces = struct.unpack_from(">H", mutable, offset)[0]
    offset += 2 + interfaces * 2

    for _ in range(2):             # fields, then methods
        count = struct.unpack_from(">H", mutable, offset)[0]
        offset += 2
        for _ in range(count):
            attributes = struct.unpack_from(">H", mutable, offset + 6)[0]
            offset += 8
            for _ in range(attributes):
                length = struct.unpack_from(">I", mutable, offset + 2)[0]
                offset += 6 + length

    patched = 0
    attributes = struct.unpack_from(">H", mutable, offset)[0]
    offset += 2
    for _ in range(attributes):
        name_index = struct.unpack_from(">H", mutable, offset)[0]
        length = struct.unpack_from(">I", mutable, offset + 2)[0]
        body = offset + 6
        if pool.utf8(name_index) == "InnerClasses":
            entries = struct.unpack_from(">H", mutable, body)[0]
            cursor = body + 2
            for _ in range(entries):
                inner_index = struct.unpack_from(">H", mutable, cursor)[0]
                inner_name = pool.class_name(inner_index)
                rule = targets.get(inner_name)
                if rule is not None:
                    clear_final, sets = rule
                    flags_offset = cursor + 6
                    flags = struct.unpack_from(">H", mutable, flags_offset)[0]
                    new_flags = (flags & CLEAR_ACCESS & ~clear_final) | sets
                    if new_flags != flags:
                        struct.pack_into(">H", mutable, flags_offset, new_flags)
                        patched += 1
                cursor += 8
        offset = body + length
    return bytes(mutable), patched


def main():
    jar, at_file, output = sys.argv[1:4]
    targets = parse_at_targets(at_file)
    if not targets:
        print("no nested-class AT targets found", file=sys.stderr)

    total = 0
    touched = 0
    with zipfile.ZipFile(jar) as src, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            if item.filename.endswith(".class"):
                patched, count = patch_inner_classes(data, targets)
                if count:
                    data = patched
                    total += count
                    touched += 1
            dst.writestr(item, data)
    print(f"patched {total} InnerClasses entries in {touched} classes -> {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
