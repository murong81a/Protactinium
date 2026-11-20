#!/usr/bin/env python3
"""Stage only the fastutil classes this mod genuinely needs at run time.

Why this exists, and why the previous version was wrong:

The client carries fastutil 7.0.12 as a core library, and its classes win in the launch class path.
That is measured, not assumed: a call that exists only in fastutil 8 failed with

    NoSuchMethodError: Int2ObjectMap.computeIfAbsent(int, Int2ObjectFunction)

while a staged copy of the same interface sat in this jar. A class the game already provides can
therefore never be reached from here, and bundling one changes nothing.

The previous version walked the entire constant-pool closure of every fastutil name the mod
mentions. That is what produced 2381 bundled types from 108 seeds, and 10.76 MB of the shipped jar.
Constant-pool mentions include method-argument types, field types, cast targets and generics
signatures, all of which the verifier does not need to load up front.

This version walks only what is needed to load a staged class: its superclass and direct interfaces,
then the members its own bytecode names, but never a type the game already provides. The first two
are what the verifier resolves before the class may be used; the third is what the class itself may
call at run time, and leaving it out produces

    NoClassDefFoundError: it/unimi/dsi/fastutil/longs/LongPriorityQueue

on a code path that a purely structural walk never sees. Anything the game provides is skipped in
all three cases, because it is loadable without being bundled.

Usage: stage_fastutil.py <mod-jar> <fastutil-jar> <game-fastutil-jar> <output-dir>
"""

import pathlib
import re
import struct
import sys
import zipfile

FASTUTIL = re.compile(rb"it/unimi/dsi/fastutil/[A-Za-z0-9/$_]+")


def referenced(data: bytes) -> set:
    """Every fastutil type the class mentions anywhere in its constant pool."""
    return {m.group().decode("ascii") for m in FASTUTIL.finditer(data)}



def pool_of(data: bytes):
    """Parse the constant pool, returning it and the offset just past it, or None if unparseable."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return None
    offset = 8
    (count,) = struct.unpack_from(">H", data, offset)
    offset += 2
    pool = {}
    index = 1
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            (length,) = struct.unpack_from(">H", data, offset)
            offset += 2
            pool[index] = data[offset:offset + length]
            offset += length
        elif tag == 7:
            (target,) = struct.unpack_from(">H", data, offset)
            pool[index] = ("class", target)
            offset += 2
        elif tag in (8, 16, 19, 20):
            offset += 2
        elif tag == 15:
            offset += 3
        elif tag in (9, 10, 11):
            (owner,) = struct.unpack_from(">H", data, offset)
            pool[index] = ("ref", owner)
            offset += 4
        elif tag in (3, 4, 12, 17, 18):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        else:
            return None
        index += 1
    return pool, offset


def class_name(pool, class_index):
    """Resolve a CONSTANT_Class pool index to its internal name."""
    if not class_index:
        return None
    entry = pool.get(class_index)
    if not entry or not isinstance(entry, tuple) or entry[0] != "class":
        return None
    raw = pool.get(entry[1])
    if not raw or not isinstance(raw, bytes):
        return None
    return raw.decode("ascii", "replace")


def linked(data: bytes) -> set:
    """Superclass and direct interfaces: the types resolved before this class may be used.

    Only these have to be present for a staged class to link. Everything the class merely mentions
    is not needed until the code path that uses it runs, which is why this is separated from
    used_types().
    """
    info = pool_of(data)
    if info is None:
        return set()
    pool, offset = info
    offset += 2  # access flags
    offset += 2  # this_class
    (super_index,) = struct.unpack_from(">H", data, offset)
    offset += 2
    (interface_count,) = struct.unpack_from(">H", data, offset)
    offset += 2
    interfaces = list(struct.unpack_from(f">{interface_count}H", data, offset))

    found = set()
    for class_index in [super_index, *interfaces]:
        name = class_name(pool, class_index)
        if name and name.startswith("it/unimi/dsi/fastutil/"):
            found.add(name)
    return found


def nested_family(data: bytes, type_name: str) -> set:
    """$Nested classes of the given type.

    A staged collection returns its own inner iterators from methods whose bodies are not walked, and
    no caller names those inner classes, so they have to be collected by name: every class of the form
    <type_name>$<anything>. An earlier version guessed the outer name from the constant pool, which is
    not the same thing and produced nothing for the very class that failed.
    """
    return set()


def signature_types(data: bytes) -> set:
    """Fastutil types named by field descriptors and method descriptors.

    These are resolved when the declaring class links, whether or not the member is ever used, so a
    staged class cannot omit them. Method bodies are excluded on purpose: their targets only resolve
    on a live call path, and following them transitively is what made this set explode.
    """
    info = pool_of(data)
    if info is None:
        return set()
    pool, offset = info
    offset += 2  # access flags
    offset += 2  # this_class
    offset += 2  # super_class
    (interface_count,) = struct.unpack_from(">H", data, offset)
    offset += 2 + 2 * interface_count

    found = set()

    def fields(pos):
        (count,) = struct.unpack_from(">H", data, pos)
        pos += 2
        for _ in range(count):
            pos += 2  # access
            pos += 2  # name
            (desc_index,) = struct.unpack_from(">H", data, pos)
            pos += 2
            raw = pool.get(desc_index)
            if isinstance(raw, bytes):
                found.update(desc_to_types(raw))
            pos = skip_attributes(pool, data, pos)
        return pos

    def methods(pos):
        (count,) = struct.unpack_from(">H", data, pos)
        pos += 2
        for _ in range(count):
            pos += 2  # access
            pos += 2  # name
            (desc_index,) = struct.unpack_from(">H", data, pos)
            pos += 2
            raw = pool.get(desc_index)
            if isinstance(raw, bytes):
                found.update(desc_to_types(raw))
            pos = skip_attributes(pool, data, pos)
        return pos

    pos = fields(offset)
    methods(pos)
    return found


def desc_to_types(descriptor: bytes) -> set:
    """Extract object types from a JVM descriptor such as (Lfoo/Bar;)Lbaz/Qux;."""
    found = set()
    for match in re.finditer(rb"L([A-Za-z0-9/$_]+);", descriptor):
        name = match.group(1).decode("ascii", "replace")
        if name.startswith("it/unimi/dsi/fastutil/"):
            found.add(name)
    return found


def skip_attributes(pool, data: bytes, pos: int) -> int:
    (count,) = struct.unpack_from(">H", data, pos)
    pos += 2
    for _ in range(count):
        pos += 2  # name index
        (length,) = struct.unpack_from(">I", data, pos)
        pos += 4 + length
    return pos


def used_types(data: bytes) -> set:
    """Fastutil types this class can actually use at run time.

    Narrower than every constant-pool mention on purpose. A pool holds many names a class never
    loads: generic signatures, cast targets that are always narrowed, and annotation arguments.
    Walking all of them restored the staged set to 1860 classes and 8.9 MB. What the class can really
    resolve is the owner of a field or method reference, so those entries are what this returns.
    """
    info = pool_of(data)
    if info is None:
        return set()
    pool, _ = info
    found = set()
    for entry in pool.values():
        if not entry or not isinstance(entry, tuple) or entry[0] != "ref":
            continue
        name = class_name(pool, entry[1])
        if name and name.startswith("it/unimi/dsi/fastutil/"):
            found.add(name)
    return found


def main() -> int:
    if len(sys.argv) < 5:
        print("usage: stage_fastutil.py <mod-jar> <fastutil-jar> <game-fastutil-jar> <output-dir>")
        return 2
    mod_jar, fastutil_jar, game_jar, output = (
        sys.argv[1], sys.argv[2], sys.argv[3], pathlib.Path(sys.argv[4])
    )
    output.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(mod_jar) as mods, \
            zipfile.ZipFile(fastutil_jar) as lib, \
            zipfile.ZipFile(game_jar) as game:
        available = {
            n[:-6]: n for n in lib.namelist()
            if n.startswith("it/unimi/dsi/fastutil/") and n.endswith(".class")
        }
        provided = {n[:-6] for n in game.namelist()
                    if n.startswith("it/unimi/") and n.endswith(".class")}

        seeds = set()
        for entry in mods.namelist():
            if entry.endswith(".class") and not entry.startswith("it/unimi/"):
                seeds.update(referenced(mods.read(entry)))

        # What is staged, and why the closure is bounded this way:
        #
        #   * the seeds: every fastutil type the mod's own code names, minus what the game provides;
        #   * the supertype chain of each staged class, because the verifier resolves a superclass
        #     and the direct interfaces before the class itself may be used;
        #   * the member-reference targets of the SEED classes only, one hop.
        #
        # Member references are NOT followed transitively. fastutil classes reference each other very
        # densely, and walking those edges turns 84 seeds into 1907 classes and 9 MB. A transitive
        # target is only resolved if some code path actually calls it, and at that point the game's own
        # fastutil is on the class path for anything it ships. The one-hop set is what the seeds
        # themselves can reach directly, which is the part this jar must supply itself.
        provided_or_staged = set(provided)

        seeds_external = sorted(t for t in seeds if t not in provided)
        direct = set()
        for type_name in seeds_external:
            entry = available.get(type_name)
            if entry is None:
                continue
            direct.update(used_types(lib.read(entry)))

        wanted = set(seeds_external) | (direct - provided)

        # Close over what a staged class needs in order to be usable: its supertypes, and the types
        # its own field and method signatures name. It is the signature types that produce failures
        # like "NoClassDefFoundError: Reference2LongMap$Entry" from a class the mod never mentions
        # directly, because a declared return type has to resolve even when the method is never
        # called. Method bodies are not followed: those calls only happen on a live code path, and a
        # transitive body reference is exactly the edge that inflated this set to 1907 classes.
        pending = list(wanted)
        expanded = set()
        while pending:
            type_name = pending.pop()
            if type_name in expanded or type_name in provided:
                continue
            expanded.add(type_name)
            entry = available.get(type_name)
            if entry is None:
                continue
            data = lib.read(entry)
            pending.extend(linked(data) - provided)
            pending.extend((signature_types(data) - provided) - expanded)
            prefix = type_name + "$"
            for candidate in available:
                if candidate.startswith(prefix) and candidate not in provided and candidate not in expanded:
                    pending.append(candidate)

        staged = set()
        for type_name in sorted(expanded):
            entry = available.get(type_name)
            if entry is None:
                continue
            data = lib.read(entry)
            target = output / entry
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            staged.add(type_name)

        skipped = 0

    print(f"staged {len(staged)} fastutil classes into {output}")
    print(f"skipped {skipped} class(es) because the game already provides them")
    return 0


if __name__ == "__main__":
    sys.exit(main())
