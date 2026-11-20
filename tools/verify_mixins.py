#!/usr/bin/env python3
"""Check that a mixin configuration only names classes that are inside the jar.

A mixin configuration naming a class the jar does not contain fails at load time with
"The specified mixin '...' was not found", which aborts the launch. That is easy to produce by
moving or renaming a mixin class while leaving its configuration entry behind, so this check runs
against the packaged jar rather than the source tree.

Usage: verify_mixins.py <jar>
"""

import json
import sys
import zipfile


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: verify_mixins.py <jar>")
        return 2
    jar_path = sys.argv[1]
    problems = 0
    with zipfile.ZipFile(jar_path) as jar:
        names = set(jar.namelist())
        configs = sorted(
            n for n in names if n.startswith("mixins.") and n.endswith(".json")
        )
        if not configs:
            print("no mixin configurations in the jar")
            return 1
        for config in configs:
            data = json.loads(jar.read(config).decode("utf-8-sig"))
            package = data.get("package", "").replace(".", "/")
            required = data.get("required", False)
            missing = []
            total = 0
            for section in ("mixins", "client", "server"):
                for entry in data.get(section, []):
                    total += 1
                    path = f"{package}/{entry.replace('.', '/')}.class"
                    if path not in names:
                        missing.append(path)
            status = "OK" if not missing else "MISSING"
            print(f"{config}: {total} mixins, {len(missing)} missing [{status}]"
                  + ("" if required else " (optional)"))
            for path in missing[:8]:
                print("    " + path)
            # An optional configuration with missing entries is tolerated by Mixin, a required one
            # is not, so only the latter counts as a failure.
            if missing and required:
                problems += len(missing)
    if problems:
        print(f"FAILED: {problems} required mixin classes are absent from the jar")
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
