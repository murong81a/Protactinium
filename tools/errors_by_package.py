#!/usr/bin/env python3
"""Group javac errors by the package that owns them.

The ported tree has hundreds of files, and the interesting question during a port is never "which
file has the most errors" but "which subsystem is unported". A per-package rollup answers that
directly; a per-file list makes it look like many unrelated problems.

Usage: errors_by_package.py <javac-log> [depth]
"""

import collections
import pathlib
import re
import sys

RECORD = re.compile(r"^([A-Za-z]:[^\r\n]*?\.java):(\d+): error: (.*)$")


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: errors_by_package.py <javac-log> [depth]")
        return 2
    log = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
    depth = int(sys.argv[2]) if len(sys.argv) > 2 else 5

    by_pkg = collections.Counter()
    by_pkg_files = collections.defaultdict(set)
    total = 0
    for line in log.splitlines():
        match = RECORD.match(line)
        if not match:
            continue
        total += 1
        path = match.group(1).replace("\\", "/")
        marker = "src/main/java/"
        if marker in path:
            relative = path.split(marker, 1)[1]
        else:
            relative = path.rsplit("/", 1)[-1]
        parts = relative.split("/")[:-1]
        if not parts:
            key = "(default)"
        else:
            key = "/".join(parts[:depth])
        by_pkg[key] += 1
        by_pkg_files[key].add(path)

    print(f"== {total} errors across {len(by_pkg)} packages")
    for key, count in by_pkg.most_common(40):
        print(f"{count:6d}  {len(by_pkg_files[key]):3d} files  {key}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
