#!/usr/bin/env python3
"""Rewrite the class file major version of every class in a jar.

Why this is needed: ForgeAutoRenamingTool 0.1.22 embeds an ASM that refuses class files newer than
it knows, so reobfuscating a jar of Java 21 classes fails with "Unsupported class file major
version 65". The port has to reobfuscate, and it cannot give up Java 21 source features (records,
pattern switches, `List.getFirst`), because the 1.12.2 upstream uses them throughout and rewriting
them all would be a separate, riskier project.

The setting this produces is honest rather than pretend: the runtime this mod targets is Java 25,
so a class file that *declares* version 61 or 65 loads either way. What matters is that the bytecode
does not use anything the declared version forbids. Java 21 adds no new bytecode instructions over
Java 17 for the constructs this tree uses (records and pattern switches are compiler desugaring),
so the version field is the only thing standing between FART and a successful run.

Usage: lower_class_version.py <input.jar> <output.jar> <major-version>
"""

import struct
import sys
import zipfile


def set_major(data: bytearray, major: int) -> int:
    """Set the class file major version, returning the previous one."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    previous = struct.unpack_from(">H", data, 6)[0]
    struct.pack_into(">H", data, 6, major)
    return previous


def main() -> int:
    if len(sys.argv) < 4:
        print("usage: lower_class_version.py <input.jar> <output.jar> <major-version>")
        return 2
    source, target, major_text = sys.argv[1], sys.argv[2], sys.argv[3]
    major = int(major_text)

    seen = {}
    changed = 0
    with zipfile.ZipFile(source) as src, zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            if item.filename.endswith(".class") and data[:4] == b"\xca\xfe\xba\xbe":
                buffer = bytearray(data)
                previous = set_major(buffer, major)
                seen[previous] = seen.get(previous, 0) + 1
                changed += 1
                data = bytes(buffer)
            dst.writestr(item, data)

    summary = ", ".join(f"{count} x major {version}" for version, count in sorted(seen.items()))
    print(f"rewrote {changed} classes to major {major} ({summary}) -> {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
