# Lessons from the 1.10.2 port

Each entry is a mistake that cost real time, the mechanism behind it, and the rule that prevents
a repeat. Written for the next session, which will otherwise rediscover all of them.

## 1. One bad file silently disables all annotation processing

**What happened.** The main module reported 576 errors, almost all `cannot find symbol` on getters
that `@Getter` was supposed to generate. Two files imported both
`net.minecraft.client.renderer.VertexBuffer` and `net.minecraft.client.renderer.vertex.VertexBuffer`,
which is an enter-phase error.

**Why.** javac runs a parse/enter phase before annotation processing. If that phase has any error,
it reports the errors and stops; processors never run. Lombok is the only processor here and
generates hundreds of accessors, so their absence turned into an error in every file that used one.
The visible error count was therefore about the missing *generated* methods, not about the two
broken imports.

**Rule.** When a build shows hundreds of "cannot find symbol" errors on generated accessors, look
for a single structural error first. Do not start editing the call sites. `-XDshould-stop.ifError=FLOW`
forces javac past enter and shows the real errors.

## 2. An access transformer does not affect compilation by itself

**What happened.** Fields that `actinium_at.cfg` widens were still `private` to the compiler.

**Why.** ForgeGradle applies the AT while it builds the deobfuscated jar. This port compiles against
`forgeBin-...-PROJECT(port).jar`, which is that pipeline's output *before* the AT step. The runtime
applies the AT again from the `FMLAT` manifest entry, so the shipped jar was always correct; only
the compiler's view was wrong.

**Rule.** The compile jar must be a copy with the AT already applied. `tools/apply_at.py` produces
it, and `build.ps1` regenerates it whenever the AT file changes.

## 3. Method AT entries carry a descriptor

**What happened.** Every method entry in the AT file was a no-op. `tools/apply_at.py` keyed its
lookup on the whole AT token, so `getFOVModifier(FZ)F` never matched the class file's separate
name and descriptor strings.

**Rule.** Parse AT member tokens into (name, descriptor) and match both. A field token has no
parentheses; a method token does. Also: `public-f` must clear `ACC_FINAL`, or a widened `final`
field still cannot be assigned.

## 4. Nested classes need their InnerClasses attribute patched too

**What happened.** `BiomeColorHelper$ColorResolver` was `public` in its own class file yet javac
still refused to name it.

**Why.** javac resolves `Foo.Bar` from the `InnerClasses` attribute of `Foo`, which carries a second
copy of `Bar`'s access flags. Widening `Bar.class` alone leaves that copy unchanged, and `javap -p`
shows the flags from the class file rather than from the attribute, so the problem is invisible.

**Rule.** `tools/patch_inner_classes.py` must run after the AT application.
`tools/class_access.py` reports both views.

## 5. Two unrelated classes are both called VertexBuffer

**What happened.** Repeated confusion between the streaming tessellator buffer and the VBO wrapper.

**Facts.** In 1.10.2, `net.minecraft.client.renderer.VertexBuffer` is what 1.12.2 calls
`BufferBuilder`. `net.minecraft.client.renderer.vertex.VertexBuffer` is the VBO wrapper and keeps
that name in both versions.

**Rule.** Never rename or import both into one file. When a file needs both, qualify one of them
inline instead of importing it.

## 6. A mixin without a refmap cannot resolve members

**What happened.** `@Shadow field buffer was not located in the target class` and, later,
`Using refmap mixins.actinium-refmap.json` with the same failure.

**Why.** In a production (obfuscated) client a mixin's member names must be mapped to SRG names. The
refmap that carries those mappings is produced by the Mixin annotation processor. This build ran
without it, so nothing was mapped. Adding the processor produced a refmap, but it only contains
entries the processor could resolve; a field whose MCP name differs from the 1.12.2 source name got
no entry at all.

**Rule.** Run the Mixin annotation processor for the module that declares mixins, and where it
cannot resolve a member, declare the shadow with the obfuscated name directly rather than assuming
an alias will be generated.

## 7. Do not package compile-only stubs

**What happened.** The first successful launch crashed with
`ClassCastException: ...Adapter cannot be cast to Comparable`.

**Why.** `top.outlands.foundation.TransformerDelegate` was declared in `mod-api-stubs` so that javac
could resolve it. The staging step packaged that module, so at runtime the module's own presence
probe found the stub, concluded the Outlands loader was installed, and took a code path that only
works with the real loader.

**Rule.** Compile-only declarations must never reach the jar. A presence probe that reads a
resource path will find whatever the jar contains, including a stand-in.

## 8. Compile against the versions the game actually ships

**What happened.** The first launch that got past mixin application died with
`NoSuchMethodError: Logger.debug(String, Object)`.

**Why.** The port added log4j 2.24 to its compile classpath. Minecraft 1.10.2 ships log4j 2.0-beta9,
which has no `debug(String, Object)` overload. The call compiled and the runtime rejected it.

**Rule.** Every library the game supplies at runtime must be the game's own version on the compile
classpath. A newer API version compiles happily and fails in the first frame that reaches it.

## 9. Injection targets are matched by descriptor, not by name

**What happened.** `Critical injection failure: callback method ... failed injection check, (0/1)
succeeded` for `SoundHandler.setListener`.

**Why.** 1.12.2 declares `setListener(Entity, float)`; 1.10.2 declares
`setListener(EntityPlayer, float)`. The name matches and the descriptor does not, so the injection
finds nothing.

**Rule.** Every `@At` target descriptor must be checked against the real jar.
`tools/check_injection_targets.py` does that for the whole tree in one pass; run it before a launch
rather than discovering mismatches one launch at a time.

## 10. A mixin can compile, ship, and still fail because one member had no refmap entry

**What happened.** The launch got further and further, then died at
`Critical injection failure: ... failed injection check, (0/1) succeeded` on a target that plainly
exists in the disassembled class.

**Why.** The target was written with its MCP name. In a production client the member carries an
obfuscated name, and the refmap maps one to the other. The Mixin annotation processor emits that
mapping only when it can resolve the member; when it cannot, it *skips the entry and reports a
warning*. The build had downgraded those warnings (`-AMSG_CANT_FIND_MAPPING=warning`), so a mixin
with an unresolvable target compiled cleanly, shipped, and failed at apply time with a message that
names the callback method rather than the missing mapping.

**Rule.** Verify refmap coverage, not just compilation. `tools/check_refmap_coverage.py` lists
mixins the refmap does not cover and `tools/find_unmapped_mixins.py` narrows that to the ones that
remap and therefore need an entry. Note the complement: a mixin declared `remap = false` references
names that are not obfuscated at all (Forge additions, mod classes, already-obfuscated names) and
correctly has no entry. Reporting every uncovered mixin as broken would be wrong in exactly that
case.

**Also.** When a specific member cannot be resolved even though it exists, declaring it with its
obfuscated name directly is a reliable fallback, and it is what `MixinTessellator` now does for
`Tessellator.field_178183_a`.

## 11. Do not measure progress by error count alone

**What happened.** The error count went up from 6 to 83 after a change that should have reduced it,
and down again after two more edits.

**Why.** Each round trip through a build took minutes and every edit invalidated the previous
count. Some counts were phantoms (see entry 1), some were real.

**Rule.** Check a specific hypothesis against the jar before editing, and verify the fix with a
targeted tool rather than inferring from the total. The tools in `tools/` exist for that reason:
`check_shadows.py`, `check_injection_targets.py`, `verify_mixins.py`, `class_access.py`.
