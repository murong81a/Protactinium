#!/usr/bin/env python3
"""Verify every @At target descriptor in the ported mixins against the real Minecraft jar.

A mixin injection is bound to a member descriptor, and a descriptor that differs from the target
class fails at apply time with "failed injection check, (0/1) succeeded". The port moved 1.12.2
sources to 1.10.2, where several signatures changed (SoundHandler.setListener takes an EntityPlayer
rather than an Entity, TESR hooks take one trailing int rather than float+int), so every descriptor
needs checking.

This reads the jar directly rather than calling javap once per class, so it can check a whole tree
in one pass.

Usage: check_injection_targets.py <source-root> <forge-jar>
"""

import pathlib
import re
import struct
import sys
import zipfile

# `target = "Lpkg/Class;method(Args)Ret"` inside an @At, and the `method = "..."` form.
OBF_TARGET = re.compile(r'target\s*=\s*"L([\w/$]+);([\w$<>]+)(\([^"]*\)[^"]*)"')
INJECT_METHOD = re.compile(r'method\s*=\s*"([\w$<>]+)(\([^"]*\)[^"]*)"')
MIXIN_TARGETS = re.compile(r"@Mixin\(\s*(?:value\s*=\s*|targets\s*=\s*\{?\s*)([^\s,)}]+)")


def methods_of(data: bytes) -> dict:
    """Return {name + descriptor: True} for every method declared in a class file."""
    count = struct.unpack_from(">H", data, 8)[0]
    offset = 10
    index = 1
    utf8 = {}
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            utf8[index] = data[offset:offset + length].decode("utf-8", "replace")
            offset += length
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

    offset += 2          # access flags
    offset += 4          # this, super
    interfaces = struct.unpack_from(">H", data, offset)[0]
    offset += 2 + interfaces * 2

    fields = struct.unpack_from(">H", data, offset)[0]
    offset += 2
    for _ in range(fields):
        attributes = struct.unpack_from(">H", data, offset + 6)[0]
        offset += 8
        for _ in range(attributes):
            length = struct.unpack_from(">I", data, offset + 2)[0]
            offset += 6 + length

    result = {}
    methods = struct.unpack_from(">H", data, offset)[0]
    offset += 2
    for _ in range(methods):
        name = utf8.get(struct.unpack_from(">H", data, offset + 2)[0], "")
        descriptor = utf8.get(struct.unpack_from(">H", data, offset + 4)[0], "")
        result[name + descriptor] = True
        attributes = struct.unpack_from(">H", data, offset + 6)[0]
        offset += 8
        for _ in range(attributes):
            length = struct.unpack_from(">I", data, offset + 2)[0]
            offset += 6 + length
    return result


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: check_injection_targets.py <source-root> <forge-jar>")
        return 2
    root = pathlib.Path(sys.argv[1])
    jar_path = sys.argv[2]

    cache = {}
    missing = []
    checked = 0
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())

        def methods_for(internal_name: str):
            if internal_name in cache:
                return cache[internal_name]
            entry = internal_name + ".class"
            result = methods_of(jar.read(entry)) if entry in names else None
            cache[internal_name] = result
            return result

        for path in root.rglob("*.java"):
            text = path.read_text(encoding="utf-8", errors="replace")
            if "@Mixin" not in text:
                continue
            for match in OBF_TARGET.finditer(text):
                owner, name, descriptor = match.group(1), match.group(2), match.group(3)
                if not owner.startswith("net/minecraft/"):
                    continue
                checked += 1
                declared = methods_for(owner)
                if declared is None:
                    missing.append((path.name, owner + "." + name, "class not in jar"))
                elif name + descriptor not in declared:
                    candidates = [k for k in declared if k.startswith(name + "(")]
                    missing.append((path.name, f"{owner}.{name}{descriptor}",
                                    "target has: " + (", ".join(sorted(candidates)[:4]) or "no such method")))

    print(f"checked {checked} @At targets against {pathlib.Path(jar_path).name}")
    if not missing:
        print("OK: every injection target exists with that descriptor")
        return 0
    print(f"{len(missing)} targets do not match:")
    for file, target, detail in missing:
        print(f"  {file}: {target}")
        print(f"      {detail}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
