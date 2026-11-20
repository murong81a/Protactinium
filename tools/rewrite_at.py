#!/usr/bin/env python3
"""Rewrite the access transformer to SRG member names for the production client.

The measured failure this fixes: the AT shipped MCP member names because the upstream project runs on
a deobfuscated runtime that uses those names. This 1.10.2 production client is SRG-named, so an AT
entry naming "RenderItem itemColors" matches nothing, the field stays private, and the first hotbar
item render dies with

    java.lang.IllegalAccessError: ... tried to access private field
    net.minecraft.client.renderer.RenderItem.field_184395_f

FML applies the AT verbatim; it does not translate names. Classic ForgeGradle performs exactly this
rewrite during reobfuscation, and this project reobfuscates classes with FART but never the AT, so the
entry set silently became inert the moment the mod left the deobf jar behind.

Entries whose member is not in the SRG table are copied unchanged: an unmapped name is not obfuscated
in production, so the MCP spelling is also the runtime spelling. Class-only entries (no member) pass
through untouched.

Usage: rewrite_at.py <at-file> <srg> <output>
"""

import sys

BS = chr(92)

SRG_FD = re.compile('^FD: (' + BS + 'S+)/(' + BS + 'w+) (' + BS + 'S+)/(' + BS + 'w+)$') if False else None

import re

SRG_FD_RE = re.compile('^FD: (' + BS + 'S+)/(' + BS + 'w+) (' + BS + 'S+)/(' + BS + 'w+)$')
SRG_MD_RE = re.compile('^MD: (' + BS + 'S+)/(' + BS + 'w+) (' + BS + 'S+) (' + BS + 'S+)/(' + BS + 'w+) (' + BS + 'S+)$')


def load_forward(srg_path):
    """(owner, mcp_name) -> srg_name, for fields and methods."""
    fields = {}
    methods = {}
    with open(srg_path, encoding='utf-8', errors='replace') as handle:
        for line in handle:
            line = line.strip()
            match = SRG_FD_RE.match(line)
            if match:
                owner, name, _, srg_name = match.groups()
                fields[(owner, name)] = srg_name
                continue
            match = SRG_MD_RE.match(line)
            if match:
                owner, name, _, _, srg_name, _ = match.groups()
                methods[(owner, name)] = srg_name
    return fields, methods


def main() -> int:
    at_path, srg_path, output = sys.argv[1], sys.argv[2], sys.argv[3]
    fields, methods = load_forward(srg_path)
    out_lines = []
    rewritten = 0
    unchanged = 0
    with open(at_path, encoding='utf-8', errors='replace') as handle:
        for raw in handle:
            line = raw.rstrip(chr(13) + chr(10))
            stripped = line.strip()
            if not stripped or stripped.startswith('#'):
                out_lines.append(line)
                continue
            parts = stripped.split()
            # Forms: <access> <class> [<member>[ <descriptor>]] [# comment]
            if len(parts) < 2:
                out_lines.append(line)
                unchanged += 1
                continue
            access, owner = parts[0], parts[1]
            if owner.startswith('public ') or owner.startswith('f '):
                out_lines.append(line)
                unchanged += 1
                continue
            comment_idx = next((i for i, p in enumerate(parts) if p == '#'), len(parts))
            member = None
            descriptor = None
            if comment_idx > 2:
                token = parts[2]
                paren = token.find('(')
                if paren >= 0:
                    member = token[:paren]
                    descriptor = token[paren:]
                else:
                    member = token
            if member is None:
                # class-only entry
                out_lines.append(line)
                unchanged += 1
                continue
            mcp_owner = owner.replace('.', '/')
            if descriptor:
                srg_name = methods.get((mcp_owner, member))
            else:
                srg_name = fields.get((mcp_owner, member))
            if srg_name is None and descriptor is None:
                # try the other table too (a method written without descriptor)
                srg_name = methods.get((mcp_owner, member))
            if srg_name is None and descriptor:
                srg_name = fields.get((mcp_owner, member))
            if srg_name is None:
                out_lines.append(line)
                unchanged += 1
                continue
            head = ' '.join(parts[:2])
            tail = ' '.join(parts[4:comment_idx]) if comment_idx > 4 else ''
            rebuilt = head + ' ' + srg_name
            if descriptor:
                # FML 1.10.2's AT parser rejects a space between member and descriptor
                # ("Invalid config file line" RuntimeException), so they stay joined exactly
                # as the source AT writes them.
                rebuilt += descriptor
            if tail:
                rebuilt += ' ' + tail
            if comment_idx < len(parts):
                rebuilt += ' ' + ' '.join(parts[comment_idx:])
            out_lines.append(rebuilt)
            rewritten += 1
    with open(output, 'w', encoding='utf-8', newline='') as handle:
        handle.write(chr(10).join(out_lines) + chr(10))
    print('AT rewritten: ' + str(rewritten) + ' member(s) to SRG, '
          + str(unchanged) + ' line(s) unchanged')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
