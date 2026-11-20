#!/usr/bin/env python3
"""Verify that a built jar is structurally sound and holds what it should.

A failed ForgeAutoRenamingTool run can leave a truncated jar behind, and the failure is easy to
miss because the tool's exit code arrives wrapped in a shell error. This check asserts the things
that make the difference between "a jar exists" and "a jar a mod loader can read":

  * the zip central directory is intact and every entry decompresses;
  * the manifest carries the coremod and access-transformer entries FML needs;
  * the expected classes and resources are actually present.

Usage: verify_jar.py <jar> [required-entry ...]
"""

import sys
import zipfile


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: verify_jar.py <jar> [required-entry ...]")
        return 2
    path = sys.argv[1]
    required = sys.argv[2:]

    problems = []
    try:
        with zipfile.ZipFile(path) as jar:
            names = jar.namelist()
            broken = jar.testzip()
            if broken is not None:
                problems.append(f"corrupt entry: {broken}")

            if "META-INF/MANIFEST.MF" not in names:
                problems.append("no META-INF/MANIFEST.MF")
            else:
                manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
                for key in ("FMLCorePlugin", "FMLCorePluginContainsFMLMod", "FMLAT"):
                    if key not in manifest:
                        problems.append(f"manifest is missing {key}")

            class_count = sum(1 for n in names if n.endswith(".class"))
            if class_count == 0:
                problems.append("jar contains no classes")

            for entry in required:
                if entry not in names:
                    problems.append(f"missing required entry: {entry}")

            print(f"{path}: {len(names)} entries, {class_count} classes")
    except zipfile.BadZipFile as error:
        problems.append(f"unreadable zip: {error}")
    except FileNotFoundError:
        problems.append("file does not exist")

    if problems:
        print("FAILED")
        for problem in problems:
            print("  - " + problem)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
