# Protactinium-dev

Actinium's optimisation half, ported to Minecraft **1.10.2** (Forge 12.18.3.2511).

This is a separate project from `../protactinium/`. That tree is the user's own 1.10.2 rendering
mod and is not related to this one; nothing here reads or writes it.

## What this is

Upstream is [`Actinium-Dev/Actinium`](https://github.com/Actinium-Dev/Actinium) at commit
`f2ad879` (2026-11-20), a rendering mod for Minecraft 1.12.2 on Cleanroom Loader. Its shape:

| Module | What it does | Status here |
| --- | --- | --- |
| `GTNHLib` | direct memory, buffers, vertex formats, VAO/VBO primitives | ported |
| `glsm` | GL state tracking, fixed-pipeline emulation, render backend abstraction | ported |
| `celeritas-common` | the chunk renderer: build, upload, cull, sort, draw | ported |
| `lwjgl2-backend` | LWJGL 2 adapter for Cleanroom's legacy launch path | not needed, see below |
| `shader` | Iris-style shader pack pipeline | compiles, not started |
| `sdl-gpu` | SDL 3 / Vulkan backend | out of scope |
| `src/main` | lifecycle, mixins, Actinium's own render code | ported |

The shader pipeline is compiled but never started. Shader pack support is not a goal of this port
yet; the code is kept compiling so that it can be finished later rather than re-ported from
scratch. See `COMPAT.md`.

## Why the toolchain looks unusual

ForgeGradle 2.3 is the only dependency resolver for 1.10.2 Forge, it requires Gradle 4.x, and that
requires JDK 8. The upstream sources use records, `var`, pattern matching and switch expressions,
so they need javac 17 or newer. Gradle and the compiler therefore cannot be the same JDK, and
Jabel does not bridge the gap on modern JDKs.

So this project does not use Gradle at all. `build.ps1` compiles each module with a modern JDK
against the deobfuscated 1.10.2 Forge jar that ForgeGradle already produced, then reobfuscates
with ForgeAutoRenamingTool and the MCP stable_29 SRG. That is the same two-step ForgeGradle would
have performed; it is just visible.

Two consequences worth knowing before editing anything:

- **The access transformer is applied to the compile classpath.** `tools/apply_at.py` writes
  `build/forgeBin-at.jar`, a copy of the Forge jar with the flags from `actinium_at.cfg` widened.
  Without it, every `private` field an AT entry widens is a compile error, even though the shipped
  jar widens it again at runtime through the `FMLAT` manifest entry.
- **Lombok is mandatory.** Upstream declares hundreds of accessors with `@Getter`/`@Setter`.
  Compiling with `-proc:none`, or letting processor discovery fall back to the JDK 21+ default,
  turns each one into a "cannot find symbol".

## Running it

The target is the user's own 1.10.2 instance, which launches on **Java 25 with LWJGL 3.3.3** via
the `lwjgl3ify` 1.10.2 port. That matters for two reasons:

1. Class file 65 loads fine, so the port ships Java 21 bytecode rather than downgrading to Java 8.
2. The runtime already provides a complete `org.lwjglx` LWJGL 2 compatibility layer inside the
   `lwjgl3ify` jar (706 classes in that jar overall), including `Display`, `ContextCapabilities`
   and the whole `org.lwjgl.util.glu` package. Ported
   code that speaks LWJGL 2 therefore keeps working without a rewrite. `libs/lwjgl3ify-compat.jar`
   is that layer, used at compile time so the port links against exactly what runs.

So the LWJGL story is: **LWJGL 2 call sites are kept, and served by the `lwjgl3ify` shim on top of
LWJGL 3.** The port does not rewrite them to LWJGL 3 and does not build its own `lwjgl2-backend`.

## Layout

```
upstream/                 pristine clone, for diffing and for the 1.12.2 side of each change
modules/mc-compat/        back-implementations of API 1.10.2 lacks   (COMPAT.md)
modules/gtnhlib/          ported GTNHLib
modules/glsm/             ported glsm, plus its lwjglCommon/lwjgl3 source roots
modules/lwjgl2/           upstream lwjgl2-backend sources, kept for reference
modules/celeritas/        ported chunk renderer
modules/shader/           ported Iris-style pipeline, compiled but not started
src/main/                 ported mod sources, resources, access transformer
libs/                     third-party compile dependencies
tools/                    the scripts this build and the port needed (see below)
build.ps1                 the whole build
```

## Tools

Every tool exists because a specific mistake cost time; the docstring in each says which.

| Tool | Purpose |
| --- | --- |
| `build.ps1` | staged compile, AT application, packaging, reobfuscation |
| `tools/apply_at.py` | widen access flags in the Forge jar so the AT affects compilation |
| `tools/java_errors.py` | turn a javac log into per-file and per-symbol counts |
| `tools/jar_grep.py` | find a constant or identifier in a jar's constant pools, without javap |
| `tools/SourceText.ps1` | read/write sources without a BOM and without changing line endings |
| `tools/audit_accessor.py` | list the receivers of a renamed accessor, to catch false positives |
| `tools/fix_glu_subclasses.py` | rewrite GLU quadric field reads for the LWJGL 3 GLU |
| `tools/strip_dh_frustums.py` | remove the Distant Horizons interfaces, which cannot exist on 1.10.2 |

## Known gaps

This section is deliberately a list of things that do **not** work, because a port that claims
universal success is untrustworthy. It is updated as the work proceeds; see `PORT_STATUS.md` for
the current build and runtime evidence.
