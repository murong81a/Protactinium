# Protactinium-dev: port status

This file records what actually works, with the evidence behind each claim. It is updated as the
port progresses; a row is only marked done when a build or a launch log proves it.

Upstream: `Actinium-Dev/Actinium` at `f2ad879` (2026-11-20).
Target: Minecraft 1.10.2, Forge 12.18.3.2511, Java 25 runtime, LWJGL 3.3.3 via the lwjgl3ify port.

## Build

| Module | Sources | Compile errors |
| --- | ---: | ---: |
| `mccompat` (back-implemented host API) | 6 | 0 |
| `gtnhlib` | 95 | 0 |
| `glsm` | 296 | 0 |
| `celeritas` (chunk renderer) | 330 | 0 |
| `shader` (Iris pipeline) | 507 | 0 |
| `modapistubs` (compile-only mod API) | 9 | 0 |
| `main` | 610 | 0 |
| **total** | **1853** | **0** |

Produced jar: `build/libs/Protactinium-alpha-0.0.1-1.10.2.jar`, 9.4 MB, 5392 classes (2602 of the
port plus a staged subset of fastutil 8), verified for manifest entries, access transformer
placement, mixin configuration coverage and zip integrity.

Compilation targets the library versions the game actually ships: guava 17.0, gson 2.2.4,
commons-lang3 3.3.2, commons-io 2.4 and log4j 2.0-beta9. That is deliberate. An earlier build used
newer versions, compiled cleanly and then died at launch with
`NoSuchMethodError: Logger.debug(String, Object)`, because log4j 2.24 has an overload the game's
2.0-beta9 does not. Compiling against the game's own versions turns that class of mistake into a
compile error.

## What the toolchain had to solve

Four problems were not visible from the source and each one cost a build:

1. **Annotation processing is skipped when the parse phase has any error.** Lombok generates
   hundreds of accessors, so one bad file made every generated method look missing across all 663
   files. Two duplicate `VertexBuffer` imports were the trigger.
2. **An access transformer does not reach the compiler by itself.** The compile jar is the
   pre-AT artifact, so `tools/apply_at.py` widens the flags first. Method entries also carry a
   descriptor, which has to be matched as a descriptor rather than as part of the name.
3. **Nested-class AT entries need a second fix.** javac resolves `Foo.Bar` through `Foo`'s
   `InnerClasses` attribute, which keeps its own copy of the flags, so
   `tools/patch_inner_classes.py` widens that copy too.
4. **A mixin needs a refmap in a production client.** Without one every `@Shadow` and `@Inject`
   member name is looked up in the wrong namespace and the first applied mixin fails. The Mixin
   annotation processor now runs for the main module and writes the SRG refmap.

## Runtime

The runtime target is the user's own 1.10.2 instance, which launches on Java 25 with
RetroFuturaBootstrap and LWJGL 3.3.3. Two consequences shaped the port:

- **LWJGL 2 call sites are kept.** The lwjgl3ify jar supplies a complete `org.lwjglx` compatibility
  layer, so `Display`, `ContextCapabilities`, `DrawableGL` and the whole `org.lwjgl.util.glu`
  package resolve on LWJGL 3. The port compiles against that layer, so it links against exactly
  what runs. `modules/lwjgl2` (upstream's own LWJGL 2 backend) is therefore not needed and is not
  built.
- **Java 21 bytecode.** The runtime is Java 25, so there is no reason to downgrade the source. The
  class file version is lowered to 61 only for the reobfuscation step, because ForgeAutoRenamingTool
  0.1.22 embeds an ASM that rejects newer files; no instruction changes.

## Feature scope

| Area | State |
| --- | --- |
| Chunk renderer (celeritas: build, upload, cull, sort, draw) | ported, compiles |
| GL state machine (glsm: state tracking, fixed-pipeline emulation) | ported, compiles |
| Direct memory / vertex formats (GTNHLib) | ported, compiles |
| Actinium's own render path (terrain, entities, TESR batching, particles, clouds) | ported, compiles |
| Iris-style shader pipeline | ported and compiles, **not started** |
| SDL / Vulkan backend | removed; the three entry points are inert stubs |
| Distant Horizons integration | faked as present so packs that require it load; renders nothing |
| Per-mod compatibility (Lumenized, LittleTiles, BetterFoliage, HBM, CoFH, Revo, ...) | isolated in `modules/mod-compat`, not built by default |

## Known gaps

Stated plainly rather than implied:

- **The game has not reached the title screen yet with this jar.** The launch gets through FML
  plugin loading, mixin configuration registration and into mixin application; the current blocker
  is recorded in `RUN_REPORT.md` when one exists.
- **Shader packs are not usable.** The pipeline compiles but is never started, so a pack placed in
  `shaderpacks/` has no effect.
- **Distant Horizons reports present but draws nothing.** That is the deliberate behaviour
  requested for this port, not an oversight; see `COMPAT.md`.
- **Per-mod integrations do not build.** Their targets have no 1.10.2 release, so the module is
  excluded from the default build and its sources are kept for a later pass.
