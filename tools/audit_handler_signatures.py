#!/usr/bin/env python3
"""Validate mixin handler signatures against the real target methods, without launching.

audit_injection_selectors.py checks that the SELECTOR names a method that exists. It cannot catch
the defect that actually blocked world entry: a handler whose parameter list is a 1.12.2 signature
(extra trailing float alpha), which fails at apply time with Invalid descriptor, Expected X but
found X F. This tool checks the other half, the handler declaration itself.

Rules implemented, matching how the runtime checks them:

  * An Inject handler's parameters must equal the target method's parameters, followed by a
    CallbackInfo. An empty capture (only the CallbackInfo) is also legal.
  * A WrapMethod handler's parameters must equal the target's, followed by Operation.
  * Selectors without a descriptor are resolved by name; if the name is unique in the target class
    the descriptor is taken from there, otherwise the check is skipped.

Only Inject and WrapMethod are checked, because their capture rules are exact matches. Other
injectors (Redirect, ModifyArg, WrapOperation, ...) capture the wrapped call's arguments instead of
the enclosing method's, so comparing against the enclosing method is wrong by construction. They
are counted in the skip total, never silently ignored.

The enclosing annotation of a selector is found by computing every annotation's paren span, so a
selector inside a WrapOperation is never misread as belonging to an earlier Inject. Regex literals
are assembled with chr(92) so that no backslash appears in this source file at all, which makes the
file immune to double escaping while being written or copied.

Usage: audit_handler_signatures.py <project-root> <forge-jar> <javap> <srg>
"""

import glob
import os
import re
import subprocess
import sys

BS = chr(92)

SELECTOR = re.compile(
    'method' + BS + 's*=' + BS + 's*' + '"([' + BS + 'w$]+)(' + BS + '([^"]*)?["&) ]')
TARGETS = re.compile(
    '@Mixin' + BS + 's*' + BS + '(' + BS + 's*(?:value' + BS + 's*=' + BS + 's*)?(['
    + BS + 'w.]+)' + BS + '.class')
TARGETS_STR = re.compile(
    '@Mixin' + BS + 's*' + BS + '(' + BS + 's*targets' + BS + 's*=' + BS + 's*' + '"(['
    + BS + 'w./]+)"')
SRG_MD = re.compile(
    '^MD: (' + BS + 'S+)/(' + BS + 'w+) (' + BS + 'S+) (' + BS + 'S+)/(' + BS + 'w+) ('
    + BS + 'S+)$')
IMPORT_RE = re.compile(
    '^import' + BS + 's+(?:static' + BS + 's+)?([' + BS + 'w.]+' + BS + '.(' + BS + 'w+));',
    re.MULTILINE)
TYPE_RE = re.compile('([' + BS + 'w$.]+)')
ANN_NAME = re.compile('@[' + BS + 'w$]+')

PRIMITIVES = {
    'double': 'D', 'float': 'F', 'int': 'I', 'long': 'J',
    'boolean': 'Z', 'byte': 'B', 'short': 'S', 'char': 'C',
}

TERMINATORS = ('CallbackInfo', 'CallbackInfoReturnable', 'Operation', 'LocalCapture')


def load_reverse(srg_path):
    methods = {}
    with open(srg_path, encoding='utf-8', errors='replace') as handle:
        for line in handle:
            match = SRG_MD.match(line.strip())
            if match:
                owner, name, desc, _, srg_name, _ = match.groups()
                methods[(owner, srg_name)] = (name, desc)
    return methods


def javap_members(javap, jar, class_name):
    result = subprocess.run(
        [javap, '-p', '-s', '-cp', jar, class_name.replace('/', '.')],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        return None
    pairs = set()
    pending = None
    for line in result.stdout.splitlines():
        stripped = line.strip()
        match = re.search('([' + BS + 'w$]+)' + BS + 's*' + BS + '(', stripped)
        if match and 'descriptor:' not in stripped and not stripped.startswith('public class'):
            pending = match.group(1)
            continue
        if stripped.startswith('descriptor:') and pending:
            pairs.add((pending, stripped[len('descriptor:'):].strip()))
            pending = None
    return pairs


def target_class(source):
    match = TARGETS_STR.search(source)
    if match:
        return match.group(1).replace('.', '/')
    match = TARGETS.search(source)
    if not match:
        return None
    name = match.group(1)
    if '.' in name:
        return name.replace('.', '/')
    imported = re.search('^import' + BS + 's+([' + BS + 'w.]*' + BS + 'b' + re.escape(name)
                         + ');', source, re.MULTILINE)
    return imported.group(1).replace('.', '/') if imported else None


def collect_imports(source):
    imports = {}
    for match in IMPORT_RE.finditer(source):
        imports[match.group(2)] = match.group(1)
    return imports


def skip_string(source, i):
    i += 1
    n = len(source)
    while i < n:
        if source[i] == BS:
            i += 2
            continue
        if source[i] == '"':
            return i + 1
        i += 1
    return n


def closing_paren(source, open_idx):
    depth = 0
    i = open_idx
    n = len(source)
    while i < n:
        ch = source[i]
        if ch == '"':
            i = skip_string(source, i)
            continue
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def annotation_spans(source):
    """Every @Name(...) annotation with its paren span; annotations without parens get the
    position of the name only. Returns a list of (start, end, name)."""
    spans = []
    for match in ANN_NAME.finditer(source):
        name = match.group(0)[1:]
        j = match.end()
        n = len(source)
        while j < n and source[j].isspace():
            j += 1
        if j < n and source[j] == '(':
            close = closing_paren(source, j)
            if close < 0:
                spans.append((match.start(), match.start() + 1, name))
                continue
            spans.append((match.start(), close + 1, name))
        else:
            spans.append((match.start(), match.end(), name))
    return spans


def scan_past_annotation(source, start):
    """start sits just inside an annotation that opened before it. Return the index just past
    the annotation's closing paren, treating string literals as opaque."""
    depth = 1
    i = start
    n = len(source)
    while i < n:
        ch = source[i]
        if ch == '"':
            i = skip_string(source, i)
            continue
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return -1


def find_handler_paren(source, from_idx):
    i = from_idx
    n = len(source)
    while i < n:
        ch = source[i]
        if ch == '"':
            i = skip_string(source, i)
            continue
        if ch == '@':
            match = ANN_NAME.match(source, i)
            if not match:
                i += 1
                continue
            j = match.end()
            while j < n and source[j].isspace():
                j += 1
            if j < n and source[j] == '(':
                close = scan_past_annotation(source, j + 1)
                if close < 0:
                    return -1
                i = close
                continue
            i = j
            continue
        if ch == '(':
            return i
        i += 1
    return -1


def split_params(text):
    parts, depth, current = [], 0, ''
    for ch in text:
        if ch in '(<[':
            depth += 1
        elif ch in ')>]':
            depth -= 1
        if ch == ',' and depth == 0:
            parts.append(current.strip())
            current = ''
        else:
            current += ch
    if current.strip():
        parts.append(current.strip())
    return parts


def param_type(decl):
    tokens = decl.replace('final ', ' ').split()
    if len(tokens) < 2:
        return None
    typ = ' '.join(tokens[:-1]).replace('[]', '')
    match = TYPE_RE.match(typ)
    return match.group(1) if match else None


def param_type_full(decl):
    """The whole type part of a declaration, keeping dotted nested forms like Outer.Inner."""
    tokens = decl.replace('final ', ' ').split()
    if len(tokens) < 2:
        return None
    return ' '.join(tokens[:-1]).replace('[]', '')


def type_to_descriptor(decl, imports):
    tokens = decl.replace('final ', ' ').split()
    if len(tokens) < 2:
        return None
    typ = ' '.join(tokens[:-1])
    dims = typ.count('[]')
    prefix = '[' * dims
    base = param_type(decl)
    if base is None:
        return None
    if base in PRIMITIVES:
        return prefix + PRIMITIVES[base]
    if base == 'String':
        return prefix + 'Ljava/lang/String;'
    resolved = imports.get(base) if '.' not in base else None
    if resolved is None and '.' in base:
        # Dotted form like Outer.Inner: resolve Outer through the import table and join the
        # nested tail with $, which is how the compiler writes it into a descriptor.
        head, _, tail = base.partition('.')
        outer = imports.get(head)
        if outer is not None:
            resolved = outer + '$' + tail
        elif head == 'net' or head == 'com':
            resolved = base  # fully qualified in place
    if resolved is None:
        return None
    return prefix + 'L' + resolved.replace('.', '/') + ';'


def handler_params(source, selector_end, imports):
    annot_close = scan_past_annotation(source, selector_end)
    if annot_close < 0:
        return None, None
    open_paren = find_handler_paren(source, annot_close)
    if open_paren < 0:
        return None, None
    close_paren = closing_paren(source, open_paren)
    if close_paren < 0:
        return None, None
    decls = split_params(source[open_paren + 1:close_paren])
    if not decls:
        return [], None
    out = []
    for decl in decls:
        base = param_type(decl)
        if base in TERMINATORS:
            return out, base
        desc = type_to_descriptor(decl, imports)
        if desc is None:
            return out, None
        out.append(desc)
    return out, None


def descriptor_params(descriptor):
    params = []
    i = 1
    while i < len(descriptor) and descriptor[i] != ')':
        start = i
        while descriptor[i] == '[':
            i += 1
        if descriptor[i] == 'L':
            i = descriptor.index(';', i) + 1
        else:
            i += 1
        params.append(descriptor[start:i])
    return params


def normalized(param_list):
    return [p.replace('$', '/') for p in param_list]


def main() -> int:
    project_root, jar, javap, srg_path = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    srg_methods = load_reverse(srg_path)
    cache = {}
    problems = []
    skipped = 0
    checked = 0

    pattern = os.path.join(project_root, 'src', 'main', 'java', '**', '*.java')
    for path in sorted(glob.glob(pattern, recursive=True)):
        with open(path, encoding='utf-8', errors='replace') as handle:
            source = handle.read()
        if '@Mixin' not in source:
            continue
        owner = target_class(source)
        if owner is None:
            continue
        imports = collect_imports(source)
        if owner not in cache:
            cache[owner] = javap_members(javap, jar, owner)
        pairs = cache[owner]
        if not pairs:
            continue
        spans = annotation_spans(source)

        for match in SELECTOR.finditer(source):
            kind = None
            for start, end, name in spans:
                if start <= match.start() < end:
                    kind = name
                    break
            if kind is None or kind not in ('Inject', 'WrapMethod'):
                skipped += 1
                continue
            declared, descriptor = match.group(1), match.group(2)

            name = declared
            if declared.startswith('func_'):
                translated = srg_methods.get((owner, declared))
                if translated:
                    name, descriptor = translated[0], translated[1]

            if descriptor is None:
                matches = [p for p in pairs if p[0] == name]
                if len(matches) != 1:
                    skipped += 1
                    continue
                descriptor = matches[0][1]

            params, terminator = handler_params(source, match.end(), imports)
            if params is None or terminator is None:
                skipped += 1
                continue
            checked += 1
            if not params:
                continue
            expected = descriptor_params(descriptor)
            if normalized(params) != normalized(expected):
                line = source[:match.start()].count(chr(10)) + 1
                got = ''.join(params)
                want = ''.join(expected)
                problems.append(
                    (os.path.relpath(path, project_root), line, owner, name,
                     'handler(' + got + ') != target(' + want + ')'))

    if problems:
        print(str(len(problems)) + ' handler signature problem(s), '
              + str(checked) + ' checked, ' + str(skipped) + ' skipped:')
        for path, line, owner, name, detail in problems:
            print('  ' + path + ':' + str(line) + '  ' + owner + '.' + name + '  ' + detail)
        return 1
    print('OK: ' + str(checked) + ' handler signature(s) match target methods ('
          + str(skipped) + ' skipped)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
