#!/usr/bin/env python3
"""Add the refmap entries the Mixin annotation processor omits for @Shadow members.

Measured behaviour of Mixin 0.8.7's annotation processor on this tree: it writes a refmap entry
for the members named by @Inject, @Redirect and @At, and for @Overwrite it writes nothing at all.
For @Shadow it also writes nothing, even though the mapping exists in the SRG and the target class
resolves. The consequence only appears at apply time, one class at a time:

    @Shadow field clouds was not located in the target class net.minecraft.client.settings.GameSettings

because the runtime client carries SRG member names (field_74345_l) and the mixin still says clouds.
Walking into that failure one class per launch would take as many launches as there are mixins.

This tool closes the gap deterministically. For every @Shadow field and method in every mixin it
builds the fully qualified member reference Mixin looks up, resolves the SRG name through the SRG
file, and writes the entry into the refmap. Entries the processor did produce are left untouched.

The key form is the one Mixin itself uses for @At FIELD and INVOKE targets, which is the form the
runtime resolves a @Shadow member through:

    L<owner>;<name>:<field descriptor>          -> L<owner>;<srg name>:<field descriptor>
    L<owner>;<name>(<args>)<return>             -> L<owner>;<srg name>(<args>)<return>

Usage: complete_shadow_refmap.py <project root> <srg file> [--check]

--check reports what is missing and exits non-zero without writing, so the build can gate on it.
"""

import glob
import json
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


def shadow_members(source):
    """Names of the members carrying @Shadow, in either annotation position."""
    return set(re.findall(r"@Shadow\b[^;{}()]*?\b(\w+)\s*[;(]", source))


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: complete_shadow_refmap.py <project root> <srg file> [--check]")
        return 2
    root, srg_path = sys.argv[1], sys.argv[2]
    check_only = "--check" in sys.argv[3:]

    refmap_path = os.path.join(root, "build", "classes", "main", "mixins.actinium-refmap.json")
    if not os.path.exists(refmap_path):
        print(f"SKIPPED: no refmap at {refmap_path}")
        return 0
    with open(refmap_path, encoding="utf-8") as handle:
        refmap = json.load(handle)
    mappings = refmap.setdefault("mappings", {})
    data = refmap.setdefault("data", {}).setdefault("searge", {})

    methods = srg_methods(srg_path)
    fields = srg_fields(srg_path)
    classes_dir = os.path.join(root, "build", "classes", "main")

    added = []
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

        for name in sorted(shadow_members(source)):
            descriptor = members.get(name)
            if descriptor is None:
                continue
            if descriptor.startswith("("):
                srg_name = methods.get((target, name, descriptor))
                if srg_name is None or srg_name == name:
                    continue
                reference = f"L{target};{name}{descriptor}"
                wanted = f"L{target};{srg_name}{descriptor}"
            else:
                srg_name = fields.get((target, name))
                if srg_name is None or srg_name == name:
                    continue
                reference = f"L{target};{name}:{descriptor}"
                wanted = f"L{target};{srg_name}:{descriptor}"

            entry = mappings.setdefault(class_key, {})
            if entry.get(reference) == wanted:
                continue
            entry[reference] = wanted
            data.setdefault(class_key, {})[reference] = wanted
            added.append(f"{class_key}: {reference} -> {srg_name}")

    if added:
        print(f"{len(added)} @Shadow refmap entr{'y' if len(added) == 1 else 'ies'} missing:")
        for line in added[:15]:
            print("   " + line)
        if len(added) > 15:
            print(f"   ... and {len(added) - 15} more")
    else:
        print("OK: every obfuscated @Shadow member has a refmap entry")

    if check_only:
        return 1 if added else 0
    if added:
        with open(refmap_path, "w", encoding="utf-8") as handle:
            json.dump(refmap, handle, indent=2)
            handle.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
