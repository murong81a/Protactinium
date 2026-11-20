"""Collect every translation key the mod's code references.

The options screen rendered raw keys such as rso.search_bar_empty and sodium.options.pages.quality,
which means the jar carried no lang files at all. This lists the keys that actually need entries so
the lang file can be written from evidence rather than from memory.
"""

import collections
import os
import re
import sys

# Keys appear either as string constants in source, or as the literal argument to a translation call.
PATTERNS = [
    re.compile(r'"(sodium\.[A-Za-z0-9_.]+)"'),
    re.compile(r'"(rso\.[A-Za-z0-9_.]+)"'),
    re.compile(r'"(options\.actinium\.[A-Za-z0-9_.]+)"'),
    re.compile(r'"(options\.iris\.[A-Za-z0-9_.]+)"'),
    re.compile(r'"(iris\.[A-Za-z0-9_.]+)"'),
    re.compile(r'"(actinium\.[A-Za-z0-9_.]+)"'),
    re.compile(r'"(protactinium\.[A-Za-z0-9_.]+)"'),
]

found = collections.Counter()
for root in sys.argv[1:]:
    for directory, _, files in os.walk(root):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(directory, name)
            try:
                with open(path, encoding="utf-8", errors="replace") as handle:
                    text = handle.read()
            except OSError:
                continue
            for pattern in PATTERNS:
                for match in pattern.finditer(text):
                    found[match.group(1)] += 1

print(f"{len(found)} distinct translation keys referenced in code")
for key in sorted(found):
    print(key)
