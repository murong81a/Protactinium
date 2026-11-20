#!/usr/bin/env python3
"""Summarise the javac log of one build stage.

The ported tree is huge, so a single compile can emit thousands of diagnostics. Reading them
through a shell pipeline loses errors to line wrapping and makes per-file counting unreliable,
which mattered repeatedly while porting (a "0 errors" reading once hid a failed build entirely).
This prints one record per diagnostic in a stable, greppable form.
"""

import collections
import pathlib
import re
import sys

RECORD = re.compile(r"^(?P<file>[A-Za-z]:[^\r\n]*?\.java):(?P<line>\d+): error: (?P<msg>.*)$")


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: java_errors.py <javac-log> [--full]")
        return 2
    log_path = pathlib.Path(sys.argv[1])
    full = "--full" in sys.argv[2:]
    text = log_path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()

    records = []
    for index, line in enumerate(lines):
        match = RECORD.match(line)
        if not match:
            continue
        detail = []
        cursor = index + 1
        while cursor < len(lines) and len(detail) < 4:
            nxt = lines[cursor]
            if RECORD.match(nxt) or re.match(r"^\d+ error", nxt):
                break
            if nxt.strip():
                detail.append(nxt.strip())
            cursor += 1
        records.append({
            "file": pathlib.PureWindowsPath(match.group("file")).name,
            "line": int(match.group("line")),
            "msg": match.group("msg").strip(),
            "detail": detail,
        })

    by_file = collections.Counter(r["file"] for r in records)
    by_msg = collections.Counter(r["msg"] for r in records)
    symbols = collections.Counter()
    for r in records:
        for d in r["detail"]:
            m = re.match(r"symbol:\s+(?:class|method|variable)\s+(\S+)", d)
            if m:
                symbols[m.group(1)] += 1

    print(f"== {log_path.name}: {len(records)} error records")
    print("\n-- per file")
    for name, count in by_file.most_common():
        print(f"{count:6d}  {name}")
    print("\n-- per message")
    for name, count in by_msg.most_common(20):
        print(f"{count:6d}  {name}")
    if symbols:
        print("\n-- missing symbols")
        for name, count in symbols.most_common(30):
            print(f"{count:6d}  {name}")
    if full:
        print("\n-- records")
        for r in records[:200]:
            print(f"{r['file']}:{r['line']}: {r['msg']}")
            for d in r["detail"]:
                print(f"    {d}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
