#!/usr/bin/env python3
"""Apply the 1.12.2 -> 1.10.2 naming and API differences that are mechanical.

Every rule here is a rename or a one-to-one API substitution whose 1.10.2 target was verified
with javap against the deobfuscated 1.10.2 Forge jar. Rules that need a behaviour decision are
NOT here; they are edited by hand so the decision is visible in the diff.

Run with --check to list files that would change without writing anything.
"""

import pathlib
import re
import sys

# (name, regex, replacement, file-name filter or None, note)
RULES = [
    (
        "GuiScreen.fontRendererObj",
        r"\.fontRenderer\b(?!\s*\()",
        ".fontRendererObj",
        None,
        "1.10.2 names GuiScreen's font renderer field fontRendererObj.",
    ),
    (
        "Minecraft.fontRenderer",
        r"Minecraft\.getMinecraft\(\)\.fontRendererObj\b",
        "Minecraft.getMinecraft().fontRendererObj",
        None,
        "kept explicit for readability",
    ),
    (
        "Vec3d components",
        r"\bvec\.x\b",
        "vec.xCoord",
        None,
        "1.10.2's Vec3d exposes xCoord/yCoord/zCoord, not x/y/z.",
    ),
    (
        "Vec3d components y",
        r"\bvec\.y\b",
        "vec.yCoord",
        None,
        "see above",
    ),
    (
        "Vec3d components z",
        r"\bvec\.z\b",
        "vec.zCoord",
        None,
        "see above",
    ),
    (
        "skyColor components",
        r"\bskyColor\.x\b",
        "skyColor.xCoord",
        None,
        "Vec3d component rename",
    ),
    (
        "skyColor components y",
        r"\bskyColor\.y\b",
        "skyColor.yCoord",
        None,
        "Vec3d component rename",
    ),
    (
        "skyColor components z",
        r"\bskyColor\.z\b",
        "skyColor.zCoord",
        None,
        "Vec3d component rename",
    ),
]


def main() -> int:
    root = pathlib.Path(sys.argv[1])
    check_only = "--check" in sys.argv[2:]
    totals = {rule[0]: 0 for rule in RULES}
    changed_files = set()

    for path in root.rglob("*.java"):
        original = path.read_text(encoding="utf-8", errors="replace")
        text = original
        for name, pattern, replacement, _filter, _note in RULES:
            text, count = re.subn(pattern, replacement, text)
            totals[name] += count
        if text != original:
            changed_files.add(path)
            if not check_only:
                path.write_text(text, encoding="utf-8", newline="\n")

    for name, pattern, replacement, _filter, note in RULES:
        print(f"{totals[name]:6d}  {name}")
        print(f"        {pattern}  ->  {replacement}")
        print(f"        {note}")
    print(f"-- {len(changed_files)} files {'would change' if check_only else 'changed'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
