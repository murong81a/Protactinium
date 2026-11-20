#!/usr/bin/env python3
"""Find mixins that need a refmap entry but will not get one.

A mixin that remaps needs every member reference mapped to an obfuscated name, so if the refmap
has no entry for it, the injection will not resolve at apply time. A mixin declared with
`remap = false` references names that are not obfuscated at all (Forge additions, mod classes, or
already-obfuscated names), and needs no entry.

So the interesting set is: mixins that remap, have no refmap entry, and therefore silently rely on
MCP names that do not exist in a production client. That is exactly the failure this port spent
most of its time on, so it is worth a tool rather than an inference.

Usage: find_unmapped_mixins.py <source-root> <jar>
"""

import json
import pathlib
import re
import sys
import zipfile

MIXIN_ANNOTATION = re.compile(r"@Mixin\(([^)]*)\)", re.DOTALL)
CLASS_DECL = re.compile(r"(?:class|interface)\s+([A-Za-z0-9_]+)")


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: find_unmapped_mixins.py <source-root> <jar>")
        return 2
    root = pathlib.Path(sys.argv[1])
    jar_path = sys.argv[2]

    with zipfile.ZipFile(jar_path) as jar:
        refmap = json.loads(jar.read("mixins.actinium-refmap.json").decode("utf-8-sig"))
        mapped = set(refmap.get("mappings", {}))

    suspects = []
    for path in root.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        annotation = MIXIN_ANNOTATION.search(text)
        if not annotation:
            continue
        options = annotation.group(1)
        remap_false = "remap = false" in options.replace("  ", " ")
        # Only the annotation on the mixin class counts; an @Inject with remap = false is not the
        # same thing.
        name_match = CLASS_DECL.search(text)
        if not name_match:
            continue
        simple = name_match.group(1)
        package = re.search(r"package\s+([\w.]+);", text)
        if not package:
            continue
        internal = package.group(1).replace(".", "/") + "/" + simple
        if internal in mapped:
            continue
        # A mixin with no member references at all cannot need mapping, but flag the rest.
        if remap_false:
            continue
        suspects.append((path.name, internal))

    print(f"{len(suspects)} remapping mixins have no refmap entry")
    for file, internal in suspects:
        print(f"  {file}  ({internal})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
