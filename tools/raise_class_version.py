#!/usr/bin/env python3
"""Raise the declared class file version of every class in a directory tree.

Purpose, and why it is safe here:

JOML is compiled to class file version 46, the level of Java 1.2, which predates StackMapTable.
Modern JVMs verify version-50-and-newer classes against stack map frames and verify older ones by
inference, and the Java 25 verifier in this instance rejects the inference path outright:

    java.lang.VerifyError: Expecting a stackmap frame at branch target 7
    Location: org/joml/Matrix4f.equals(Lorg/joml/Matrix4fc;F)Z @2: if_acmpne

The bytecode itself is valid; only the declared version is behind the JVM's expectation. Raising the
version is the standard remedy and is what newer JOML releases rely on, but JOML keeps the low
version for Android compatibility, so it has to be done at package time here.

This is deliberately separate from lower_class_version.py, which exists for the opposite direction
and for a jar rather than a directory.

Usage: raise_class_version.py <directory> <major-version> [--report]
"""

import os
import struct
import sys

MAGIC = b"\xca\xfe\xba\xbe"


def raise_one(path: str, major: int) -> int:
    """Set the class file major version, returning the previous one."""
    with open(path, "r+b") as handle:
        header = handle.read(8)
        if header[:4] != MAGIC:
            return -1
        previous = struct.unpack_from(">H", header, 6)[0]
        if previous >= major:
            return previous
        handle.seek(6)
        handle.write(struct.pack(">H", major))
        return previous


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: raise_class_version.py <directory> <major-version> [--report]")
        return 2
    root, major = sys.argv[1], int(sys.argv[2])
    report = "--report" in sys.argv[3:]

    changed = 0
    seen = {}
    for directory, _, files in os.walk(root):
        for name in files:
            if not name.endswith(".class"):
                continue
            path = os.path.join(directory, name)
            previous = raise_one(path, major)
            if previous < 0:
                continue
            seen[previous] = seen.get(previous, 0) + 1
            if previous < major:
                changed += 1

    if report:
        for version in sorted(seen):
            print(f"   version {version}: {seen[version]} class(es)")
    print(f"raised {changed} class file(s) to version {major} under {root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
