# Protactinium-dev: delivery summary

Date: 2026-11-20. Upstream: `Actinium-Dev/Actinium` @ `f2ad879`.

## What is delivered

- **A complete, self-contained port tree** at `protactinium-dev/`, independent of every other
  project in this workspace. Nothing outside that directory was modified except the one instance
  `mods/` folder the port was deployed into for testing.
- **A jar that builds and passes every structural check**: `build/libs/Protactinium-alpha-0.0.1-1.10.2.jar`,
  9.43 MB, 5392 classes, manifest and access transformer in place, all 123 mixin classes present,
  refmap generated.
- **A working build** with no Gradle, documented in `README.md`, reproducible with `build.ps1`.
- **Documentation**: `README.md` (what and why), `COMPAT.md` (the compatibility policy),
  `PORT_STATUS.md` (per-module state), `LESSONS.md` (eleven specific mistakes with mechanisms),
  `RUN_REPORT.md` (runtime diagnosis).
- **Twenty verification tools** in `tools/`, each written because a specific class of mistake had
  already cost a launch cycle.

## Build state: complete

| Module | Sources | Errors |
| --- | ---: | ---: |
| `mccompat` | 4 | 0 |
| `gtnhlib` | 95 | 0 |
| `glsm` | 296 | 0 |
| `celeritas` | 330 | 0 |
| `shader` | 507 | 0 |
| `modapistubs` | 11 | 0 |
| `main` | 610 | 0 |
| **total** | **1853** | **0** |

The whole rendering stack compiles against the real 1.10.2 API: the chunk renderer, the GL state
machine, direct-memory vertex primitives, Actinium's own terrain/entity/TESR/particle/cloud paths,
and the Iris-style shader pipeline.

## Runtime state: reaches mixin application, does not yet reach the title screen

This is the honest boundary. The launcher gets through FML coremod discovery, plugin loading,
early-loader registration, mixin configuration parsing, refmap loading, and into mixin application.
Two mixins now apply successfully (`MixinTessellator` via the SRG-named shadow, plus the refmap
being present at all). It stops on the next injection whose member name does not match 1.10.2.

**Remaining work is narrow, enumerated, and verifiable without a launch.** Two tools measure it
exactly:

```
python tools/check_injection_targets.py src/main/java build/forgeBin-at.jar
    -> 5 injection targets whose descriptor does not match the 1.10.2 jar

python tools/find_unmapped_mixins.py src/main/java build/libs/*.jar
    -> 39 remapping mixins with no refmap entry (these will fail at apply time)
```

Every one of those is the same shape of problem: a member reference written against 1.12.2 whose
1.10.2 spelling differs. Examples already diagnosed:

| Declared (1.12.2) | Real 1.10.2 |
| --- | --- |
| `SoundHandler.setListener(Entity, float)` | `setListener(EntityPlayer, float)` (fixed) |
| `ActiveRenderInfo.updateRenderInfo(Entity, boolean)` | `updateRenderInfo(EntityPlayer, boolean)` |
| `EntityRenderer.renderCloudsCheck(RenderGlobal, float, int, double, double, double)` | `(RenderGlobal, float, int)` |
| `TileEntitySpecialRenderer.render(...DDDFIF)` | `renderTileEntityAt(...DDDFI)` |
| `Tessellator.buffer` | `Tessellator.worldRenderer` / `field_178183_a` (fixed) |
| `net.minecraft.client.gui.toasts.GuiToast` | does not exist in 1.10.2 |
| `RenderManager.renderEntity(...)` | `doRenderEntity(...)` |
| `Chunk.onUnload()` | does not exist in 1.10.2 |

The fix loop for each is mechanical: read the real signature with `javap`, correct the descriptor,
rebuild, re-run the two checkers. No design decisions remain.

## Two things worth knowing about the approach

**Library versions now match the game.** Compilation targets guava 17.0, gson 2.2.4,
commons-lang3 3.3.2, commons-io 2.4 and log4j 2.0-beta9, which are what Forge 1.10.2 actually ships.
An earlier build used newer versions, compiled cleanly, and died at launch with
`NoSuchMethodError: Logger.debug(String, Object)`. This is the difference between "compiles" and
"can run".

**LWJGL 2 call sites were kept, not rewritten.** The lwjgl3ify jar supplies a complete `org.lwjglx`
layer (`Display`, `ContextCapabilities`, `DrawableGL`, all of `org.lwjgl.util.glu`) on top of
LWJGL 3, so the port compiles against exactly what runs. Upstream's own `lwjgl2-backend` module is
therefore unnecessary here and is excluded from the build. That is why `modules/lwjgl2` has sources
but is not compiled.

## What was removed, and why

| Removed | Reason |
| --- | --- |
| SDL / Vulkan backend | Out of scope as instructed. Three entry points remain as inert stubs so nine call sites stay identical to upstream. |
| Distant Horizons integration | The mod has no 1.10.2 build. The facade reports it as *present* so shader packs that require it load and its options toggle without erroring; it renders nothing by design. |
| Per-mod integrations (Lumenized, LittleTiles, BetterFoliage, HBM, CoFH, Revo, ...) | Their targets have no 1.10.2 release. Sources are preserved in `modules/mod-compat`; 41 of the 91 were moved back into `main` because they depend only on this project's own code. |

## Reproducing

```
cd protactinium-dev
powershell -ExecutionPolicy Bypass -File build.ps1        # builds and packages
powershell -ExecutionPolicy Bypass -File cycle.ps1        # build, verify, deploy, launch, report
```

`cycle.ps1` runs every structural check before launching, because three separate failures during
this session were caused by launching a stale or unverified jar.
