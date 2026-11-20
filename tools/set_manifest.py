#!/usr/bin/env python3
"""Replace the manifest of an existing jar without touching any class.

Why this is needed: ForgeAutoRenamingTool writes a manifest of its own over the one handed to `jar`,
so an attribute set at packaging time is gone from the shipped artifact. The attribute this tree
depends on is Lwjgl3ify-Aware, which tells lwjgl3ify not to rewrite org.lwjgl.opengl references into
org.lwjglx ones; the ported LWJGL 3 render backend links against GLDebugMessageCallback, which the
lwjglx shim does not carry, so without the attribute the backend fails to load with
"NoClassDefFoundError: org/lwjglx/opengl/GLDebugMessageCallbackI" before any context exists.

The rewrite goes through a temporary file and is verified by reading the result back, so a failure
leaves the build without a jar rather than with a broken one.

Usage: set_manifest.py <jar> <manifest file>
"""

import os
import sys
import zipfile


def read_manifest(path: str) -> bytes:
    with open(path, encoding="ascii", errors="replace") as handle:
        text = handle.read()
    lines = [line.rstrip() for line in text.replace("\r\n", "\n").split("\n") if line.strip()]
    joined = "\r\n".join(lines) + "\r\n\r\n"
    return joined.encode("ascii")


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: set_manifest.py <jar> <manifest file>")
        return 2
    jar_path, manifest_path = sys.argv[1], sys.argv[2]
    if not os.path.exists(jar_path):
        print(f"FAILED: no jar at {jar_path}")
        return 1

    wanted = read_manifest(manifest_path)
    temporary = jar_path + ".tmp"

    with zipfile.ZipFile(jar_path) as source, zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as target:
        for item in source.infolist():
            if item.filename.upper() == "META-INF/MANIFEST.MF":
                continue
            target.writestr(item, source.read(item.filename))
        target.writestr("META-INF/MANIFEST.MF", wanted)

    with zipfile.ZipFile(temporary) as check:
        written = check.read("META-INF/MANIFEST.MF")
        if written != wanted:
            print("FAILED: the manifest did not survive the rewrite")
            os.remove(temporary)
            return 1

    os.replace(temporary, jar_path)
    print(f"manifest restored in {os.path.basename(jar_path)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
