#!/usr/bin/env python3
"""Rewrite the GLU quadric subclasses for the LWJGL 3 GLU implementation.

Upstream Angelica subclasses LWJGL 2's org.lwjgl.util.glu.Sphere/Disk/Cylinder/PartialDisk and
reads the protected fields of Quadric (textureFlag, normals, orientation, drawStyle) directly
with `super.field`. The GLU implementation that ships with the LWJGL 3 runtime exposes those
same values through accessor methods and keeps the fields private, so every `super.<field>`
read becomes a `super.get<Field>()` call.

Done as a script rather than by hand because the four files contain 130-odd of these reads and
a missed one is a compile error, while a wrongly rewritten one is a silent behaviour change.
"""

import pathlib
import re
import sys

FIELD_TO_GETTER = {
    "textureFlag": "getTextureFlag",
    "normals": "getNormals",
    "orientation": "getOrientation",
    "drawStyle": "getDrawStyle",
}

TARGETS = [
    "AngelicaSphere.java",
    "AngelicaDisk.java",
    "AngelicaCylinder.java",
    "AngelicaPartialDisk.java",
]


def rewrite(text: str) -> tuple[str, int]:
    total = 0
    for field, getter in FIELD_TO_GETTER.items():
        pattern = re.compile(r"\bsuper\." + field + r"\b")
        text, count = pattern.subn("super." + getter + "()", text)
        total += count
    # A write through the old field name would now be a compile error; report it rather than
    # silently leaving the file broken.
    leftovers = re.findall(r"\bsuper\.(textureFlag|normals|orientation|drawStyle)\s*=", text)
    if leftovers:
        raise SystemExit("unhandled assignment to a Quadric field: " + ", ".join(leftovers))
    return text, total


def main() -> int:
    root = pathlib.Path(sys.argv[1])
    changed = 0
    for name in TARGETS:
        path = root / name
        if not path.is_file():
            print(f"missing: {path}")
            continue
        original = path.read_text(encoding="utf-8")
        updated, count = rewrite(original)
        if updated != original:
            path.write_text(updated, encoding="utf-8", newline="\n")
            changed += 1
            print(f"{name}: {count} field reads rewritten")
        else:
            print(f"{name}: no change")
    print(f"-- {changed} files updated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
