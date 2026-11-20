#!/usr/bin/env python3
"""Strip the Distant Horizons interfaces from the Iris shadow frustum classes.

Distant Horizons is a 1.12.2-only mod and has no 1.10.2 build, so its API cannot be on this
port's classpath. Upstream already treats it as optional: every use is marked
`@Optional.Interface` / `@Optional.Method(modid = "distanthorizons")`, which means the class
already works without DH present. Removing the interface from the declaration and dropping the
two DH-only methods therefore changes nothing about the behaviour the port can reach: DH is never
loaded, so those methods would never be called either way.

What is deliberately preserved: the classes stay `extends Frustum`, keep their vanilla
`isBoundingBoxInFrustum` / `setPosition` behaviour, and keep the Celeritas viewport interface.
Only the DH interface and its two methods go.
"""

import pathlib
import re
import sys

TARGETS = [
    "CullEverythingFrustum.java",
    "AdvancedShadowCullingFrustum.java",
    "BoxCullingFrustum.java",
    "NonCullingFrustum.java",
]

DH_IMPORT = re.compile(r"^import com\.seibel\.distanthorizons\.[^\r\n]*\r?\n", re.MULTILINE)
DH_ANNOTATION = re.compile(
    r"^@Optional\.Interface\(modid = \"distanthorizons\",[^\r\n]*\r?\n", re.MULTILINE
)
# The two DH-only methods, with their optional-method annotation.
DH_METHOD = re.compile(
    r"\r?\n\t@Optional\.Method\(modid = \"distanthorizons\"\)\r?\n"
    r"\t@Override\r?\n"
    r"\tpublic (?:void update|boolean intersects)\([^)]*\) \{.*?\r?\n\t\}\r?\n",
    re.DOTALL,
)


def rewrite(text: str) -> tuple[str, list[str]]:
    notes = []
    new, count = DH_IMPORT.subn("", text)
    if count:
        notes.append(f"{count} DH imports removed")

    new, count = DH_ANNOTATION.subn("", new)
    if count:
        notes.append(f"{count} @Optional.Interface annotations removed")

    new, count = DH_METHOD.subn("\n", new)
    if count:
        notes.append(f"{count} DH-only methods removed")

    # Remove the interface name from the implements list, wherever it sits.
    new, count = re.subn(r",\s*IDhApiShadowCullingFrustum", "", new)
    if count:
        notes.append("interface removed from implements list")

    # The @Optional import is only used for DH once the methods are gone.
    if "Optional." not in new.replace("import net.minecraftforge.fml.common.Optional;", ""):
        new = new.replace("import net.minecraftforge.fml.common.Optional;\n", "")
        new = new.replace("import net.minecraftforge.fml.common.Optional;\r\n", "")
        notes.append("unused Optional import removed")

    leftovers = [line for line in new.splitlines() if "distanthorizons" in line or "DhApi" in line or "IDhApi" in line]
    if leftovers:
        raise SystemExit("leftover DH references in output:\n" + "\n".join(leftovers))
    return new, notes


def main() -> int:
    root = pathlib.Path(sys.argv[1])
    for name in TARGETS:
        matches = list(root.rglob(name))
        if not matches:
            print(f"missing: {name}")
            continue
        path = matches[0]
        original = path.read_text(encoding="utf-8")
        updated, notes = rewrite(original)
        if updated != original:
            path.write_text(updated, encoding="utf-8", newline="\n")
            print(f"{name}: {'; '.join(notes)}")
        else:
            print(f"{name}: no change")
    return 0


if __name__ == "__main__":
    sys.exit(main())
