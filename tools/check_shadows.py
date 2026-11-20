#!/usr/bin/env python3
"""Check every @Shadow member in the ported mixins against the real Minecraft jar.

A `@Shadow` declaration must name the target's member exactly as it exists in the version being
run. The port moved 1.12.2 sources to 1.10.2, where some members were renamed (`Tessellator.buffer`
is `worldRenderer`), and a mismatch only surfaces at runtime as
"@Shadow field X was not located in the target class". Catching it here turns a launch-time failure
into a list.

Usage: check_shadows.py <source-root> <forge-jar>
"""

import functools
import pathlib
import re
import subprocess
import sys

MIXIN_TARGET = re.compile(r"@Mixin\(\s*(?:value\s*=\s*)?([A-Za-z0-9_.]+)\.class")
SHADOW_FIELD = re.compile(
    r"@Shadow\b(?P<between>[^;{}]*?);", re.DOTALL
)
JAVA_PATH = r"D:\JAVA\Java17\bin\javap.exe"


@functools.lru_cache(maxsize=None)
def fields_of(jar: str, class_name: str) -> tuple:
    """Field names declared on a class, via javap."""
    try:
        output = subprocess.run(
            [JAVA_PATH, "-p", "-classpath", jar, class_name],
            capture_output=True, text=True, timeout=60,
        ).stdout
    except (OSError, subprocess.SubprocessError):
        return ()
    names = []
    for line in output.splitlines():
        line = line.strip()
        if not line.endswith(";"):
            continue
        # The last identifier before the semicolon is the member name.
        match = re.search(r"([A-Za-z0-9_$]+)\s*;\s*$", line)
        if match:
            names.append(match.group(1))
    return tuple(names)


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: check_shadows.py <source-root> <forge-jar>")
        return 2
    root = pathlib.Path(sys.argv[1])
    jar = sys.argv[2]

    checked = 0
    missing = []
    for path in root.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        target_match = MIXIN_TARGET.search(text)
        if not target_match:
            continue
        target = target_match.group(1)
        for shadow in SHADOW_FIELD.finditer(text):
            declaration = shadow.group("between")
            name_match = re.search(r"([A-Za-z0-9_$]+)\s*$", declaration.strip())
            if not name_match:
                continue
            name = name_match.group(1)
            if not re.search(r"\b(private|protected|public)\b", declaration):
                continue
            checked += 1
            known = fields_of(jar, target)
            if not known:
                continue
            if name not in known:
                missing.append((path.name, target, name, known))

    print(f"checked {checked} shadow fields against {pathlib.Path(jar).name}")
    if not missing:
        print("OK: every shadow field exists on its target")
        return 0
    print(f"{len(missing)} shadow fields do not exist on their target:")
    for file, target, name, known in missing:
        print(f"  {file}: {target}.{name}")
        print(f"      target has: {', '.join(sorted(known)[:12])}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
