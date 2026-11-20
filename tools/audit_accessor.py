#!/usr/bin/env python3
"""Audit call sites of a renamed accessor.

The 1.12.2 -> 1.10.2 port renames ResourceLocation.getPath() to getResourcePath() and
getNamespace() to getResourceDomain(). A blanket text replacement is only safe if every rewritten
call really is a ResourceLocation: the ported tree also contains its own types with a getPath()
method (OptionIdentifier, PBRType, ...), where the rename would be wrong. This prints the receiver
expression of every rewritten call so the false positives can be found and reverted by hand.

Usage: audit_accessor.py <root> <method-name>
"""

import pathlib
import re
import sys


def main() -> int:
    root = pathlib.Path(sys.argv[1])
    method = sys.argv[2]
    pattern = re.compile(r"([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\." + re.escape(method) + r"\(")
    receivers: dict[str, int] = {}
    for path in root.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for match in pattern.finditer(text):
            receiver = match.group(1)
            receivers[receiver] = receivers.get(receiver, 0) + 1
    for receiver, count in sorted(receivers.items(), key=lambda kv: -kv[1]):
        print(f"{count:5d}  {receiver}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
