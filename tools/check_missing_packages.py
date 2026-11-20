"""Report external package references that the runtime cannot satisfy.

The video settings crash was a NoClassDefFoundError for org.slf4j.LoggerFactory: a class the ported
source referenced, that neither Minecraft 1.10.2 nor this jar provides. The same shape of defect can
exist anywhere else, and it only shows up when the code path runs, which is why it survived to a
user-visible crash instead of failing at build time.

This compares every package the mod's own classes import against what the game jar and the mod jar
actually contain, and lists the gaps grouped by how likely they are to be fatal.
"""

import collections
import re
import sys
import zipfile

IMPORT = re.compile(rb"^import\s+(?:static\s+)?([a-zA-Z][\w.]*)\s*;", re.MULTILINE)


def packages_of(jar_path):
    with zipfile.ZipFile(jar_path) as jar:
        names = jar.namelist()
    return {n.rsplit("/", 1)[0].replace("/", ".") for n in names if n.endswith(".class")}


def main() -> int:
    mod_jar, forge_jar = sys.argv[1], sys.argv[2]
    provided = packages_of(forge_jar) | packages_of(mod_jar)

    ours = collections.Counter()
    with zipfile.ZipFile(mod_jar) as jar:
        for name in jar.namelist():
            if not name.endswith(".class") or name.startswith("it/unimi/"):
                continue
            data = jar.read(name)
            for match in IMPORT.finditer(data):
                package = match.group(1).decode("ascii").rsplit(".", 1)[0]
                if package.startswith(("java.", "javax.", "jdk.", "sun.")):
                    continue
                if package in provided:
                    continue
                ours[package] += 1

    if not ours:
        print("OK: every imported package is present in the runtime")
        return 0

    print("packages imported but absent from the runtime:")
    for package, count in ours.most_common():
        print(f"  {count:4d} reference(s)  {package}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
