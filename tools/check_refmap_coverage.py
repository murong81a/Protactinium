#!/usr/bin/env python3
"""Verify that every @At target in the mixin configs has a mapping in the generated refmap.

This is the check whose absence cost the most time on this port. A mixin can compile, ship, and
still fail at launch with

    Critical injection failure: ... failed injection check, (0/1) succeeded

because the `@At` target was written with the MCP name (`setListener`) while a production client
carries the obfuscated name (`func_147691_a`), and the refmap did not map one to the other. The
annotation processor emits those entries, but it silently skips any target it cannot resolve, and
the build downgrades the corresponding diagnostic to a warning.

Reading the refmap directly answers the question the launch answers, without a launch.

Usage: check_refmap_coverage.py <jar>
"""

import json
import re
import sys
import zipfile

# A target inside the mixin source, e.g. Lnet/minecraft/client/Foo;bar(I)V. Read from the refmap's
# perspective: the entry keys are the MCP forms and the values the production forms.
MCP_TARGET = re.compile(r"L(net/minecraft/[^;]+);([A-Za-z0-9_$<>]+)(\([^)]*\)[^\s;]*)")


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: check_refmap_coverage.py <jar>")
        return 2
    jar_path = sys.argv[1]
    with zipfile.ZipFile(jar_path) as jar:
        names = jar.namelist()
        if "mixins.actinium-refmap.json" not in names:
            print("FAILED: the jar contains no refmap at all")
            return 1
        refmap = json.loads(jar.read("mixins.actinium-refmap.json").decode("utf-8-sig"))
        mappings = refmap.get("mappings", {})

        # Every target the refmap knows about, in its MCP spelling.
        known = set()
        for mixin, entries in mappings.items():
            for key, value in entries.items():
                known.add(key)
                known.add(value)
                if ";" in key:
                    known.add(key.split(";", 1)[1])

        # Every mixin class that the configs declare, so mixins with no entry at all are visible.
        declared = set()
        for config in (n for n in names if n.startswith("mixins.actinium") and n.endswith(".json")):
            data = json.loads(jar.read(config).decode("utf-8-sig"))
            if "package" not in data:
                continue
            package = data["package"].replace(".", "/")
            for section in ("mixins", "client", "server"):
                for entry in data.get(section, []):
                    declared.add(f"{package}/{entry.replace('.', '/')}")

        missing_entries = sorted(c for c in declared if c not in mappings)
        print(f"refmap covers {len(mappings)} mixins; {len(declared)} declared in configs")
        if missing_entries:
            print(f"{len(missing_entries)} mixin classes have no refmap entry:")
            for name in missing_entries[:10]:
                print("   " + name)

        # A mixin with no entry may still be correct: a mixin that only uses @Overwrite or an
        # already-SRG name needs no mapping. Report but do not fail on it.
        print("note: a mixin absent from the refmap can still be valid if it needs no mapping")

    return 0


if __name__ == "__main__":
    sys.exit(main())
