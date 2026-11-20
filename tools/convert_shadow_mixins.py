#!/usr/bin/env python3
"""Rewrite @Shadow members to their SRG names with remapping disabled.

Why this shape, in the order it was established:

  1. Declaring the member with its MCP name and relying on the refmap was measured to fail on this
     tree: with the correct refmap entry present in the packaged jar, Mixin still threw
     "@Shadow field clouds was not located in the target class net.minecraft.client.settings.GameSettings".
  2. Declaring the member with its SRG name and remap = false was measured to work: the same mixin
     applied and the launch advanced to an unrelated failure.

So the SRG name goes in the declaration. This tree already used that shape by hand in one place,
MixinTessellator shadowing field_178183_a, which is what identified the working form.

A @Shadow member is referenced by name inside the mixin too, so every reference in the same file is
renamed with it. The rewrite is restricted to one file at a time and the file is compiled afterwards,
so a bad rename surfaces as a compile error rather than as a silently different value at runtime.

Usage: convert_shadow_mixins.py <project root> <srg file> [--check]

--check reports what would change and exits non-zero, so the build can gate on it.
"""

import glob
import os
import re
import subprocess
import sys

JAVAP = "javap"


def srg_methods(srg_path):
    index = {}
    pattern = re.compile(r"^MD: (\S+)/(\w+) (\S+) (\S+)/(\w+) (\S+)$")
    with open(srg_path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = pattern.match(line.strip())
            if match:
                owner, name, desc, _, srg_name, _ = match.groups()
                index[(owner, name, desc)] = srg_name
    return index


def srg_fields(srg_path):
    index = {}
    pattern = re.compile(r"^FD: (\S+)/(\w+) (\S+)/(\w+)$")
    with open(srg_path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = pattern.match(line.strip())
            if match:
                owner, name, _, srg_name = match.groups()
                index[(owner, name)] = srg_name
    return index


def target_class(source):
    match = re.search(r'@Mixin\s*\(\s*targets\s*=\s*"([\w./]+)"', source)
    if match:
        return match.group(1).replace(".", "/")
    match = re.search(r"@Mixin\s*\(\s*(?:value\s*=\s*)?([\w.]+)\.class", source)
    if not match:
        return None
    name = match.group(1)
    if "." in name:
        return name.replace(".", "/")
    imported = re.search(r"^import\s+([\w.]*\b" + re.escape(name) + r");", source, re.MULTILINE)
    return imported.group(1).replace(".", "/") if imported else None


def compiled_members(classes_dir, class_key):
    """Map member name to descriptor for the compiled mixin class."""
    found = {}
    path = os.path.join(classes_dir, *class_key.split("/")) + ".class"
    if not os.path.exists(path):
        return found
    try:
        output = subprocess.run(
            [JAVAP, "-p", "-s", "-cp", classes_dir, class_key.replace("/", ".")],
            capture_output=True,
            text=True,
            timeout=60,
        ).stdout
    except (OSError, subprocess.SubProcessError):
        return found
    pending = None
    for line in output.splitlines():
        stripped = line.strip()
        method = re.search(r"([\w$]+)\s*\(", stripped)
        if method and "descriptor:" not in stripped and not stripped.startswith("public class"):
            pending = method.group(1)
            continue
        field = re.search(r"([\w$]+);$", stripped)
        if field and "descriptor:" not in stripped and pending is None:
            pending = field.group(1)
            continue
        if stripped.startswith("descriptor:") and pending:
            found[pending] = stripped[len("descriptor:"):].strip()
            pending = None
    return found


# @Shadow, optionally with arguments and with @Final or another annotation after it, followed by the
# member declaration this annotation applies to.
# @Shadow, then any number of annotations such as @Final or @Mutable, then the declaration. The
# @Final case is what made an earlier version of this tool report success while leaving
# MixinTextureAtlas.mapUploadedSprites in MCP form: the annotation line between the two was not
# accounted for, so the pattern matched nothing and the file was skipped as already correct.
SHADOW_SPAN = re.compile(
    r"@Shadow\b(?P<args>\s*\([^)]*\))?\s*"
    r"(?P<annotations>(?:@[\w.]+(?:\s*\([^)]*\))?\s*)*)"
    r"(?P<decl>(?:public |protected |private |static |final |volatile )*"
    r"[\w.<>\[\], ?]+?\s+(?P<name>\w+)\s*[;(])"
)


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: convert_shadow_mixins.py <project root> <srg file> [--check]")
        return 2
    root, srg_path = sys.argv[1], sys.argv[2]
    check_only = "--check" in sys.argv[3:]

    methods = srg_methods(srg_path)
    fields = srg_fields(srg_path)
    classes_dir = os.path.join(root, "build", "classes", "main")

    changes = []
    pattern = os.path.join(root, "src", "main", "java", "**", "*.java")
    for path in sorted(glob.glob(pattern, recursive=True)):
        with open(path, encoding="utf-8", errors="replace") as handle:
            source = handle.read()
        if "@Shadow" not in source:
            continue
        target = target_class(source)
        if target is None:
            continue
        package = re.search(r"package\s+([\w.]+);", source).group(1).replace(".", "/")
        class_key = package + "/" + os.path.splitext(os.path.basename(path))[0]
        members = compiled_members(classes_dir, class_key)
        if not members:
            continue

        renames = {}
        for match in SHADOW_SPAN.finditer(source):
            name = match.group("name")
            descriptor = members.get(name)
            if descriptor is None:
                continue
            if descriptor.startswith("("):
                srg_name = methods.get((target, name, descriptor))
            else:
                srg_name = fields.get((target, name))
            if srg_name is None or srg_name == name:
                continue
            renames[name] = srg_name

        if not renames:
            continue

        for name, srg_name in sorted(renames.items()):
            changes.append(f"{class_key}: {name} -> {srg_name}")

        if check_only:
            continue

        # Rename the declaration and every reference to it inside this mixin. A reference to a
        # shadowed member is either `this.name` or a bare `name`; anything preceded by a dot belongs
        # to another object and must not be touched. Some shadowed names are ordinary words (`red`,
        # `alpha`, `posX`), so the dotted form is what keeps this from rewriting a different class's
        # field of the same name. The module is compiled afterwards, so a leftover reference shows up
        # as a compile error rather than as a silently wrong value.
        for name in sorted(renames, key=len, reverse=True):
            source = re.sub(r"(?<![\w$.])this\s*\.\s*" + re.escape(name) + r"(?![\w$])",
                            "this." + renames[name], source)
            source = re.sub(r"(?<![\w$.])" + re.escape(name) + r"(?![\w$])", renames[name], source)

        # Every renamed member is now an SRG name, so the annotations must stop remapping.
        source = re.sub(r"@Shadow\b(?!\s*\()", "@Shadow(remap = false)", source)
        source = re.sub(r"@Shadow\(\s*\)", "@Shadow(remap = false)", source)
        source = re.sub(r"@Shadow\(\s*remap\s*=\s*false\s*\)", "@Shadow(remap = false)", source)

        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(source)

    if changes:
        print(f"{len(changes)} @Shadow member(s) to convert to SRG names:")
        for line in changes[:20]:
            print("   " + line)
        if len(changes) > 20:
            print(f"   ... and {len(changes) - 20} more")
    else:
        print("OK: every @Shadow member already uses its SRG name")
    if check_only:
        return 1 if changes else 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
