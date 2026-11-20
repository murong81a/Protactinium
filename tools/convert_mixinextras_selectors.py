#!/usr/bin/env python3
"""Rewrite MixinExtras method selectors to their SRG names.

The Mixin annotation processor remaps the members it knows how to read: the ``method`` attribute of
@Inject, @Redirect and @At, and @Shadow declarations. It does not remap the ``method`` attribute of
the MixinExtras injectors (@WrapMethod, @WrapOperation, @ModifyExpressionValue, @ModifyReturnValue,
@WrapWithCondition), because those annotations belong to a different processor that this build does
not run. The selector therefore ships as written, and a production client resolves it against a class
whose members are named func_* and field_*:

    Critical injection failure: @WrapMethod annotation on actinium$markWorldRendering could not find
    any targets matching 'renderWorld(FJ)V' in net/minecraft/client/renderer/EntityRenderer

Each such failure costs a launch, and there were 24 selectors, so they are converted in one pass.
The SRG name is resolved through the SRG file by the target class and the current MCP name; when a
selector carries a descriptor it is used directly, and when it does not the SRG is searched for every
overload and the rewrite is only made when the name is unambiguous (a name that maps to one SRG name
across all its overloads).

Usage: convert_mixinextras_selectors.py <project root> <srg file> [--check]
"""

import glob
import os
import re
import sys

ANNOTATION = re.compile(
    r"@(?P<kind>WrapMethod|WrapOperation|ModifyExpressionValue|ModifyReturnValue|WrapWithCondition)\s*\("
)
SELECTOR = re.compile(r'method\s*=\s*(?P<value>"[^"]*"|\{[^}]*\})')


def srg_methods(srg_path):
    """(owner, mcp name) -> set of SRG names, and (owner, mcp name, descriptor) -> SRG name."""
    by_descriptor = {}
    by_name = {}
    pattern = re.compile(r"^MD: (\S+)/(\w+) (\S+) (\S+)/(\w+) (\S+)$")
    with open(srg_path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = pattern.match(line.strip())
            if not match:
                continue
            owner, name, descriptor, _, srg_name, _ = match.groups()
            by_descriptor[(owner, name, descriptor)] = srg_name
            by_name.setdefault((owner, name), set()).add(srg_name)
    return by_descriptor, by_name


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


def split_selector(text):
    """Split an MCP selector such as ``renderWorld(FJ)V`` into name and descriptor."""
    match = re.match(r"^([\w$<>]+)(\(.*)?$", text)
    if not match:
        return None, None
    return match.group(1), match.group(2)


def annotation_spans(source):
    """Yield (start, end, body) for each MixinExtras annotation call, balancing parentheses.

    A regex cannot do this correctly: the annotation body nests @At(...) groups whose own ``target``
    strings mention the same member names, and rewriting those would corrupt the injection point.
    Only the top-level ``method`` selector of the MixinExtras annotation may be touched.
    """
    for match in ANNOTATION.finditer(source):
        depth = 1
        index = match.end()
        while index < len(source) and depth:
            character = source[index]
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
            index += 1
        if depth == 0:
            yield match.start(), index, source[match.end():index - 1], match.group("kind")


def carries_srg_names(project_root, target):
    """Whether the compiled target class actually contains SRG member names."""
    import os
    import re
    path = os.path.join(project_root, "build", "forgeBin-at.jar")
    try:
        import zipfile
        with zipfile.ZipFile(path) as jar:
            data = jar.read(target + ".class")
        return re.search(rb"func_\d+_[a-z]", data) is not None
    except (OSError, KeyError):
        # Unknown: assume translated, which keeps the old behaviour rather than silently skipping.
        return True


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: convert_mixinextras_selectors.py <project root> <srg file> [--check]")
        return 2
    root, srg_path = sys.argv[1], sys.argv[2]
    project_root = root
    check_only = "--check" in sys.argv[3:]

    by_descriptor, by_name = srg_methods(srg_path)

    changes = []
    pattern = os.path.join(root, "src", "main", "java", "**", "*.java")
    for path in sorted(glob.glob(pattern, recursive=True)):
        with open(path, encoding="utf-8", errors="replace") as handle:
            source = handle.read()
        if not any(tag in source for tag in
                   ("@WrapMethod", "@WrapOperation", "@ModifyExpressionValue",
                    "@ModifyReturnValue", "@WrapWithCondition")):
            continue
        target = target_class(source)
        if target is None:
            continue
        # A target the game does not obfuscate keeps its own member names in a production client, so
        # writing an SRG selector there resolves to nothing and the apply fails. Several renderer
        # classes are in exactly that position, TileEntityChestRenderer among them.
        if not carries_srg_names(project_root, target):
            continue

        pieces = []
        cursor = 0
        for start, end, body, kind in annotation_spans(source):
            selector = SELECTOR.search(body)
            if selector is None:
                continue
            raw = selector.group("value")
            entries = re.findall(r'"([^"]*)"', raw)
            converted = []
            touched = False
            for entry in entries:
                name, descriptor = split_selector(entry)
                if name is None:
                    converted.append(entry)
                    continue
                if descriptor and descriptor != "()V":
                    srg_name = by_descriptor.get((target, name, descriptor))
                else:
                    candidates = by_name.get((target, name), set())
                    srg_name = next(iter(candidates)) if len(candidates) == 1 else None
                if srg_name is None or srg_name == name:
                    converted.append(entry)
                    continue
                converted.append(srg_name + (descriptor or ""))
                touched = True
                changes.append(f"{os.path.basename(path)}: {entry} -> {srg_name}")
            if not touched:
                continue
            replacement = 'method = ' + (
                "{" + ", ".join('"' + item + '"' for item in converted) + "}"
                if raw.startswith("{") else '"' + converted[0] + '"'
            )
            new_body = body[:selector.start()] + replacement + body[selector.end():]
            pieces.append(source[cursor:start])
            pieces.append(source[start:end].replace(body, new_body, 1))
            cursor = end
        if not pieces:
            continue
        pieces.append(source[cursor:])
        updated = "".join(pieces)

        if updated != source and not check_only:
            with open(path, "w", encoding="utf-8", newline="\n") as handle:
                handle.write(updated)

    if changes:
        print(f"{len(changes)} MixinExtras selector(s) to convert to SRG names:")
        for line in changes[:20]:
            print("   " + line)
        if len(changes) > 20:
            print(f"   ... and {len(changes) - 20} more")
    else:
        print("OK: every MixinExtras method selector already uses its SRG name")
    if check_only:
        return 1 if changes else 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
