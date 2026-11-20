#!/usr/bin/env python3
"""Report the access flags of classes in a jar, including nested ones.

An access transformer that names a nested class (`Foo$Bar`) only widens that exact class file.
The enclosing class being public does not make the nested one public, and the InnerClasses
attribute in the enclosing class is metadata only: javac reads the access flags of the class file
it is asked to load. This script answers "is this class actually public in the jar I compile
against" for both the outer and the nested entries, which is the question that kept being guessed
at while wiring the AT.

Usage: class_access.py <jar> <entry-prefix> [entry-prefix ...]
"""

import struct
import sys
import zipfile

ACC_PUBLIC = 0x0001
ACC_PRIVATE = 0x0002
ACC_PROTECTED = 0x0004
ACC_FINAL = 0x0010
ACC_INTERFACE = 0x0200


def class_access(data):
    count = struct.unpack_from(">H", data, 8)[0]
    offset = 10
    index = 1
    while index < count:
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
    return struct.unpack_from(">H", data, offset)[0]


def describe(flags):
    parts = []
    if flags & ACC_PUBLIC:
        parts.append("public")
    if flags & ACC_PRIVATE:
        parts.append("private")
    if flags & ACC_PROTECTED:
        parts.append("protected")
    if flags & ACC_FINAL:
        parts.append("final")
    if flags & ACC_INTERFACE:
        parts.append("interface")
    if not parts:
        parts.append("package-private")
    return " ".join(parts)


def main() -> int:
    jar = sys.argv[1]
    prefixes = sys.argv[2:]
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if not name.endswith(".class"):
                continue
            if prefixes and not any(prefix.replace(".", "/") in name for prefix in prefixes):
                continue
            flags = class_access(archive.read(name))
            print(f"0x{flags:04x}  {describe(flags):28s} {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
