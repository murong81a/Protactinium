#!/usr/bin/env python3
"""Rewrite @Overwrite methods to SRG names with remapping disabled.

Background, and why this is not a cosmetic edit:

  * The Mixin annotation processor emits a refmap entry for every member it must rename on a
    production client: @Shadow fields and methods, and the members named by @Inject, @Redirect and
    @At. It emits NO entry for the body of an @Overwrite method, so naming that method with its MCP
    name leaves the mixin unresolvable and the launch dies with
    "@Overwrite method renderSkybox(IIF)V ... was not located in the target class".
  * Hand-writing those refmap entries does not help: entries are keyed by member reference, and
    @Overwrite resolves through the target's own member lookup rather than through the entry. This
    was measured: with a correct entry present in the packaged refmap the apply still failed.
  * The form that works is to declare the method with its SRG name and set remap = false for that
    annotation. Only @Overwrite is converted, because @Shadow through the refmap already works and
    rewriting it would churn members that need no change.

Usage: convert_overwrite_mixins.py <project root> <srg file> [--check]

--check reports what would change and exits non-zero, so a build can use it as a gate.
"""

import glob
import os
import re
import subprocess
import sys

JAVAP = "javap"


def srg_methods(srg_path):
    """(owner, mcp name, mcp descriptor) -> SRG name, from the MD: lines of the SRG."""
    index = {}
    pattern = re.compile(r"^MD: (\S+)/(\w+) (\S+) (\S+)/(\w+) (\S+)$")
    with open(srg_path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = pattern.match(line.strip())
            if match:
                owner, name, desc, _, srg_name, _ = match.groups()
                index[(owner, name, desc)] = srg_name
    return index


def target_class(source):
    """Fully qualified name of the @Mixin target, resolved through the source's imports."""
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


DECLARATION = re.compile(
    r"@Overwrite\b(?P<paren>\s*\([^)]*\))?\s*\n(?P<leading>(?:\s*(?:@\w+[^\n]*\n|//[^\n]*\n|\*[^\n]*\n|/\*\*?[^\n]*\n))*"
    r"\s*(?:public|protected|private)?\s*(?:static\s+)?(?P<type>[\w.<>\[\], ?]+?)\s+)(?P<name>\w+)(?P<tail>\s*\()"
)


def class_descriptors(classes_dir, class_key):
    """Map method name to descriptor for the compiled mixin class."""
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
        if stripped.startswith("descriptor:") and pending:
            found[pending] = stripped[len("descriptor:"):].strip()
            pending = None
    return found


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: convert_overwrite_mixins.py <project root> <srg file> [--check]")
        return 2
    root, srg_path = sys.argv[1], sys.argv[2]
    check_only = "--check" in sys.argv[3:]

    methods = srg_methods(srg_path)
    classes_dir = os.path.join(root, "build", "classes", "main")

    changes = []
    pattern = os.path.join(root, "src", "main", "java", "**", "*.java")
    for path in sorted(glob.glob(pattern, recursive=True)):
        with open(path, encoding="utf-8", errors="replace") as handle:
            source = handle.read()
        if "@Overwrite" not in source:
            continue
        target = target_class(source)
        if target is None:
            continue
        package = re.search(r"package\s+([\w.]+);", source).group(1).replace(".", "/")
        class_key = package + "/" + os.path.splitext(os.path.basename(path))[0]
        descriptors = class_descriptors(classes_dir, class_key)

        def rewrite(match):
            name = match.group("name")
            descriptor = descriptors.get(name)
            if descriptor is None:
                return match.group(0)
            srg_name = methods.get((target, name, descriptor))
            if srg_name is None or srg_name == name:
                return match.group(0)
            changes.append(f"{class_key}: {name}{descriptor} -> {srg_name}")
            return (
                "@Overwrite(remap = false)\n"
                + match.group("leading")
                + srg_name
                + match.group("tail")
            )

        updated = DECLARATION.sub(rewrite, source)
        if updated != source and not check_only:
            with open(path, "w", encoding="utf-8", newline="\n") as handle:
                handle.write(updated)

    if changes:
        print(f"{len(changes)} @Overwrite method(s) to convert to SRG names:")
        for line in changes:
            print("   " + line)
    else:
        print("OK: every @Overwrite method already uses its SRG name")
    if check_only:
        return 1 if changes else 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
