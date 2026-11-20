#!/usr/bin/env python3
"""Apply an access transformer to a jar by widening member access flags.

Why this exists: ForgeGradle normally applies the mod's access transformer while it builds the
deobfuscated Minecraft jar, so the compiler sees the widened members. This port compiles directly
against `forgeBin-1.10.2-...-PROJECT(port).jar`, which is that pipeline's output *before* the AT
step. Compiling against it means every AT line in actinium_at.cfg has no effect at compile time,
which shows up as "private access" errors on members upstream can read freely.

The runtime applies the AT again through the jar manifest (`FMLAT: protactinium_at.cfg`), so this
script only has to make the compile-time view agree with the runtime view. It rewrites access
flags and removes `final` where the AT asks for it; it changes no bytecode.

AT syntax understood here:

    <access> <class>                       class-level entry
    <access> <class> <field>               field entry
    <access> <class> <method>(<args>)<ret> method entry, descriptor optional

Access keywords: `public`, `public-f`, `protected`, `protected-f`, `private`, `private-f`.
The `-f` suffix additionally clears ACC_FINAL, which is what lets an AT widen a `final` field.

Two mistakes this script is written to avoid, both found by testing against the real jar:

  * A method entry in the AT file carries a descriptor (`getFOVModifier(FZ)F`) while the class
    file stores the bare name plus a separate descriptor string. Keying the rule on the whole AT
    token silently matches nothing, and every method AT entry becomes a no-op.
  * `public-f` is not just `public`. A field that stays ACC_FINAL cannot be assigned even after it
    becomes public, so the final flag has to be cleared as well.
"""

import struct
import sys
import zipfile

ACC_PUBLIC = 0x0001
ACC_PRIVATE = 0x0002
ACC_PROTECTED = 0x0004
ACC_FINAL = 0x0010

CLEAR_ACCESS = ~(ACC_PRIVATE | ACC_PROTECTED | ACC_PUBLIC) & 0xFFFF

# AT keyword -> (additional bits to clear, bits to set)
ACCESS = {
    "public": (0, ACC_PUBLIC),
    "public-f": (ACC_FINAL, ACC_PUBLIC),
    "protected": (0, ACC_PROTECTED),
    "protected-f": (ACC_FINAL, ACC_PROTECTED),
    "private": (0, ACC_PRIVATE),
    "private-f": (ACC_FINAL, ACC_PRIVATE),
}


def parse_member(token):
    """Split an AT member token into (name, descriptor-or-None).

    A field token has no parentheses; a method token carries a JVM descriptor.
    """
    if "(" not in token:
        return token, None
    return token.split("(", 1)[0], token[token.index("("):]


def parse_at(path):
    entries = []
    unknown = []
    with open(path, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) < 2:
                unknown.append(line)
                continue
            access, subject = parts[0], parts[1].replace(".", "/")
            if access not in ACCESS:
                unknown.append(line)
                continue
            clear_final, sets = ACCESS[access]
            if len(parts) == 2:
                # Class-level entry: widen the class access flags.
                entries.append((subject, None, None, clear_final, sets))
            else:
                name, descriptor = parse_member(parts[2])
                entries.append((subject, name, descriptor, clear_final, sets))
    return entries, unknown


class ClassFile:
    """Constant-pool aware class file mutator for access flags only."""

    def __init__(self, data):
        self.data = bytearray(data)

    def _walk_constant_pool(self):
        """Return (end-offset, {index: utf8-string})."""
        count = struct.unpack_from(">H", self.data, 8)[0]
        offset = 10
        index = 1
        strings = {}
        while index < count:
            tag = self.data[offset]
            offset += 1
            if tag == 1:
                length = struct.unpack_from(">H", self.data, offset)[0]
                offset += 2
                strings[index] = self.data[offset:offset + length].decode("utf-8", "replace")
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
                raise ValueError(f"unknown constant pool tag {tag}")
            index += 1
            if tag in (5, 6):
                index += 1
        return offset, strings

    def apply(self, rules):
        """rules: list of (name, descriptor_or_None, clear_final, set_bits)."""
        cp_end, strings = self._walk_constant_pool()
        offset = cp_end + 2  # skip access_flags
        offset += 4  # this_class, super_class
        interfaces = struct.unpack_from(">H", self.data, offset)[0]
        offset += 2 + interfaces * 2

        changed = 0
        for _section in range(2):  # fields, then methods
            count = struct.unpack_from(">H", self.data, offset)[0]
            offset += 2
            for _ in range(count):
                name = strings.get(struct.unpack_from(">H", self.data, offset + 2)[0])
                descriptor = strings.get(struct.unpack_from(">H", self.data, offset + 4)[0])
                for rule_name, rule_desc, clear_final, set_bits in rules:
                    if rule_name != name:
                        continue
                    if rule_desc is not None and rule_desc != descriptor:
                        continue
                    flags = struct.unpack_from(">H", self.data, offset)[0]
                    new_flags = (flags & CLEAR_ACCESS & ~clear_final) | set_bits
                    if new_flags != flags:
                        struct.pack_into(">H", self.data, offset, new_flags)
                        changed += 1
                attrs = struct.unpack_from(">H", self.data, offset + 6)[0]
                offset += 8
                for _ in range(attrs):
                    length = struct.unpack_from(">I", self.data, offset + 2)[0]
                    offset += 6 + length
        return changed

    def apply_class(self, clear_final, set_bits):
        cp_end, _ = self._walk_constant_pool()
        flags = struct.unpack_from(">H", self.data, cp_end)[0]
        new_flags = (flags & CLEAR_ACCESS & ~clear_final) | set_bits
        struct.pack_into(">H", self.data, cp_end, new_flags)
        return new_flags != flags


def main():
    if len(sys.argv) < 4:
        print("usage: apply_at.py <input.jar> <at-file> <output.jar>")
        return 2
    input_jar, at_file, output_jar = sys.argv[1:4]
    entries, unknown = parse_at(at_file)
    if unknown:
        print(f"{len(unknown)} unparsed AT lines:", file=sys.stderr)
        for line in unknown[:10]:
            print("   " + line, file=sys.stderr)

    by_class = {}
    for subject, name, descriptor, clear_final, sets in entries:
        by_class.setdefault(subject, {"class": None, "members": []})
        if name is None:
            by_class[subject]["class"] = (clear_final, sets)
        else:
            by_class[subject]["members"].append((name, descriptor, clear_final, sets))

    touched = 0
    changed_members = 0
    with zipfile.ZipFile(input_jar) as src, zipfile.ZipFile(output_jar, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            name = item.filename[:-6] if item.filename.endswith(".class") else None
            spec = by_class.get(name) if name else None
            if spec is not None:
                editor = ClassFile(data)
                if spec["class"] is not None:
                    editor.apply_class(*spec["class"])
                if spec["members"]:
                    changed_members += editor.apply(spec["members"])
                data = bytes(editor.data)
                touched += 1
            dst.writestr(item, data)

    print(f"applied AT to {touched} classes, {changed_members} members -> {output_jar}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
