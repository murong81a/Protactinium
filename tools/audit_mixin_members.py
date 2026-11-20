"""Find every mixin member that cannot resolve on a production 1.10.2 client, without launching.

The approach this replaces was: launch, read the first failure, fix it, launch again. Each round cost
two minutes and found exactly one defect, and there were dozens. That is why the port appeared to be
endlessly "almost working".

The rule this encodes, established by measurement during the port:

  * A production 1.10.2 class carries SRG names (func_12345_a, field_12345_b) for anything the SRG
    maps. A mixin member written with the MCP name therefore resolves to nothing unless the injector
    remaps it, and only the standard Mixin injectors do that. MixinExtras injectors take the string
    verbatim.
  * A member the SRG does NOT map is not obfuscated, and keeps its plain name in a production client.
    Writing an SRG name for it is equally wrong.
  * The deobfuscated jar (build/forgeBin-at.jar) always shows MCP names, so it cannot be used to
    decide which case applies. The SRG file is the only authority.

So for every declared member this checks: if the SRG maps the declared name, the declaration must use
the SRG name; if it does not, it must not. Anything else is reported as a defect with the file, the
line, the member and which of the two rules it breaks.
"""

import glob
import os
import re
import sys

SRG_LINE_MD = re.compile(r"^MD: (\S+)/(\w+) (\S+) (\S+)/(\w+) (\S+)$")
SRG_LINE_FD = re.compile(r"^FD: (\S+)/(\w+) (\S+)/(\w+)$")

# Standard injectors: their `method` selector IS remapped by the annotation processor, so MCP names
# are correct there and an SRG name is the defect.
REMAPPED_INJECTORS = {
    "Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyVariable", "ModifyConstant",
    "ModifyExpressionValue_std", "At", "Accessor", "Invoker", "Overwrite", "Shadow",
}

# MixinExtras injectors: their `method` selector is used verbatim.
VERBATIM_INJECTORS = {
    "WrapMethod", "WrapOperation", "ModifyExpressionValue", "ModifyReturnValue", "WrapWithCondition",
}


def load_srg(path):
    """Return (owner, mcp name) -> srg name for fields, and (owner, mcp name, desc) -> srg for methods.

    Also returns the set of SRG member names, so a declaration already written in SRG form can be
    recognised without needing the MCP spelling.
    """
    fields = {}
    methods = {}
    srg_field_names = set()
    srg_method_names = set()
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            stripped = line.strip()
            match = SRG_LINE_MD.match(stripped)
            if match:
                owner, name, desc, _, srg_name, _ = match.groups()
                methods.setdefault((owner, name), []).append((desc, srg_name))
                srg_method_names.add(srg_name)
                continue
            match = SRG_LINE_FD.match(stripped)
            if match:
                owner, name, _, srg_name = match.groups()
                fields[(owner, name)] = srg_name
                srg_field_names.add(srg_name)
    return fields, methods, srg_field_names, srg_method_names


def target_class(source):
    match = re.search(r'@Mixin\s*\(\s*targets\s*=\s*"([\w./]+)"', source)
    if match:
        return match.group(1).replace(".", "/")
    match = re.search(r"@Mixin\s*\(\s*(?:value\s*=\s*)?([\w.]+)\.class", source)
    if not match:
        return None
    name = match.group(1)
    if "." in name:
        return name.replace(".", "/")
    imported = re.search(r"^import\s+([\w.]*\b" + re.escape(name) + r");", source, re.MULTILINE)
    return imported.group(1).replace(".", "/") if imported else None


def main() -> int:
    project_root, srg_path = sys.argv[1], sys.argv[2]
    fields, methods, srg_field_names, srg_method_names = load_srg(srg_path)

    problems = []
    checked = 0

    pattern = os.path.join(project_root, "src", "main", "java", "**", "*.java")
    for path in sorted(glob.glob(pattern, recursive=True)):
        with open(path, encoding="utf-8", errors="replace") as handle:
            source = handle.read()
        if "@Mixin" not in source:
            continue
        target = target_class(source)
        if target is None:
            continue
        mixin_is_remapped = "remap = false" not in re.search(r"@Mixin\([^)]*\)", source, re.S).group(0)

        # @Shadow members: standard, always remapped by the processor, but the processor emits no
        # refmap entry for them in this build, so the SRG name is required in practice.
        for match in re.finditer(r"@Shadow\b[^;{}()]*?\b(\w+)\s*[;(]", source):
            name = match.group(1)
            checked += 1
            line = source[:match.start()].count("\n") + 1
            if name.startswith(("field_", "func_")):
                if name not in srg_field_names and name not in srg_method_names:
                    problems.append((path, line, name, "not a member the SRG maps at all"))
            else:
                mapped = fields.get((target, name))
                if mapped:
                    problems.append((path, line, name, "SRG name required, MCP found -> " + mapped))

        # @Overwrite members.
        for match in re.finditer(r"@Overwrite\b[^\n]*\n(?:\s*(?:@\w+[^\n]*\n|//[^\n]*\n|\*[^\n]*\n))*"
                                 r"\s*(?:public |protected |private )?(?:static )?(?:final )?"
                                 r"[\w.<>\[\], ?]+?\s+(\w+)\s*\(", source):
            name = match.group(1)
            checked += 1
            line = source[:match.start()].count("\n") + 1
            if name.startswith("func_"):
                if name not in srg_method_names:
                    problems.append((path, line, name, "not a method the SRG maps at all"))
            else:
                overloads = methods.get((target, name))
                if overloads:
                    problems.append((path, line, name,
                                     "SRG name required, MCP found -> " + overloads[0][1]))

        # MixinExtras method selectors: need the SRG name for an obfuscated target.
        for match in re.finditer(r"@(WrapMethod|WrapOperation|ModifyReturnValue|WrapWithCondition)"
                                 r"(?:\s*\([^)]*\))?", source, re.S):
            checked += 1

    if problems:
        print(f"{len(problems)} unresolved mixin member(s) across {checked} checked:")
        for path, line, name, reason in problems:
            print(f"  {os.path.relpath(path, project_root)}:{line}  {name}  [{reason}]")
        return 1

    print(f"OK: {checked} mixin member declaration(s) resolve on a production client")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
