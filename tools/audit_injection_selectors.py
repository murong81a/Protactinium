"""Check every mixin injection selector against the real 1.10.2 classes, without launching.

Launching to discover one broken selector at a time is what made this port look perpetually nearly
working: each round cost minutes and found exactly one defect out of dozens.

The subtlety that this tool has to get right, and that cost several launches during the port:

  * The jar used for existence checking (build/forgeBin-at.jar) is DEOBFUSCATED, so it contains MCP
    names.
  * A selector may legitimately be written in either the MCP spelling (standard injectors, whose
    selector the annotation processor remaps) or the SRG spelling (MixinExtras injectors, whose
    selector is taken verbatim).
  * Therefore a selector name must be translated BACK to its MCP name before being looked up in that
    jar. Looking up an SRG name there reports a false failure for a correct selector, which is exactly
    the mistake this file made on its first version.
"""

import glob
import os
import re
import subprocess
import sys

SELECTOR = re.compile(r'method\s*=\s*"([\w$]+)(\([^"]*)?["&) ]')
TARGETS = re.compile(r'@Mixin\s*\(\s*(?:value\s*=\s*)?([\w.]+)\.class')
TARGETS_STR = re.compile(r'@Mixin\s*\(\s*targets\s*=\s*"([\w./]+)"')
SRG_MD = re.compile(r"^MD: (\S+)/(\w+) (\S+) (\S+)/(\w+) (\S+)$")
SRG_FD = re.compile(r"^FD: (\S+)/(\w+) (\S+)/(\w+)$")


def load_reverse(srg_path):
    """SRG name -> MCP name, plus owner-scoped variants."""
    methods = {}
    fields = {}
    with open(srg_path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            stripped = line.strip()
            match = SRG_MD.match(stripped)
            if match:
                owner, name, desc, _, srg_name, _ = match.groups()
                methods[(owner, srg_name)] = (name, desc)
                continue
            match = SRG_FD.match(stripped)
            if match:
                owner, name, _, srg_name = match.groups()
                fields[(owner, srg_name)] = name
    return methods, fields


def javap_members(javap, jar, class_name):
    result = subprocess.run(
        [javap, "-p", "-s", "-cp", jar, class_name.replace("/", ".")],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        return None, None
    names = set()
    pairs = set()
    pending = None
    for line in result.stdout.splitlines():
        stripped = line.strip()
        match = re.search(r"([\w$]+)\s*\(", stripped)
        if match and "descriptor:" not in stripped and not stripped.startswith("public class"):
            pending = match.group(1)
            names.add(pending)
            continue
        if stripped.startswith("descriptor:") and pending:
            pairs.add((pending, stripped[len("descriptor:"):].strip()))
            pending = None
    return names, pairs


def target_class(source):
    match = TARGETS_STR.search(source)
    if match:
        return match.group(1).replace(".", "/")
    match = TARGETS.search(source)
    if not match:
        return None
    name = match.group(1)
    if "." in name:
        return name.replace(".", "/")
    imported = re.search(r"^import\s+([\w.]*\b" + re.escape(name) + r");", source, re.MULTILINE)
    return imported.group(1).replace(".", "/") if imported else None


def main() -> int:
    project_root, jar, javap, srg_path = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    srg_methods, _ = load_reverse(srg_path)
    cache = {}
    problems = []
    checked = 0

    pattern = os.path.join(project_root, "src", "main", "java", "**", "*.java")
    for path in sorted(glob.glob(pattern, recursive=True)):
        with open(path, encoding="utf-8", errors="replace") as handle:
            source = handle.read()
        if "@Mixin" not in source:
            continue
        owner = target_class(source)
        if owner is None:
            continue
        if owner not in cache:
            cache[owner] = javap_members(javap, jar, owner)
        names, pairs = cache[owner]
        if names is None:
            continue

        for match in SELECTOR.finditer(source):
            declared = match.group(1)
            descriptor = match.group(2)
            checked += 1
            line = source[:match.start()].count("\n") + 1

            # Accept either spelling: translate an SRG name back to MCP for the lookup.
            name = declared
            if declared.startswith("func_"):
                translated = srg_methods.get((owner, declared))
                if translated:
                    name = translated[0]

            if name not in names:
                problems.append((path, line, owner, declared, descriptor, "no such method"))
            elif descriptor and (name, descriptor) not in pairs:
                problems.append((path, line, owner, declared, descriptor, "descriptor mismatch"))

    if problems:
        print(f"{len(problems)} selector problem(s) out of {checked} checked:")
        for path, line, owner, name, descriptor, reason in problems:
            print(f"  {os.path.relpath(path, project_root)}:{line}  {owner}.{name}{descriptor or ''}"
                  f"  [{reason}]")
        return 1

    print(f"OK: {checked} injection selector(s) resolve against {os.path.basename(jar)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
