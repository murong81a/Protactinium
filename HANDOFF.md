# Handoff: read this first

Everything a fresh session (or a compacted context) needs to continue this port without
rediscovering it. Written 2026-11-20 at the point where the build is complete and the runtime is
being finished.

## 1. What this project is

A port of [`Actinium-Dev/Actinium`](https://github.com/Actinium-Dev/Actinium) @ `f2ad879`
(Minecraft 1.12.2 / Cleanroom) to **Minecraft 1.10.2 / Forge 12.18.3.2511**.

- Project root: `D:\MuRong\cwsgi12313\MRPlus\protactinium-dev`
- Deliverable: `build/libs/Protactinium-alpha-0.0.1-1.10.2.jar`
- The untouched upstream clone is at `upstream/` inside the project, for diffing.
- `protactinium/`, `lwjgl3ify-1102/` and `_tmp/ysm/` in the same workspace are **other people's
  projects**. Do not modify them. This port only reads `_tmp/ysm` (the prebuilt 1.10.2 Forge jar
  and the MCP SRG) and the user's instance at `D:\pcl\versions\1.10.2-Forge_12.18.3.2511`.
- `D:\pcl\versions\1.10.2-Forge_12.18.3.2511-OptiFine_I5` is **read only** if it exists; it does
  not exist on this machine anyway.

## 2. Commands that matter

```powershell
cd D:\MuRong\cwsgi12313\MRPlus\protactinium-dev

# full build: compiles 7 modules, packages, reobfuscates -> build\libs\*.jar
powershell -ExecutionPolicy Bypass -File build.ps1

# quick compile check, no packaging
powershell -ExecutionPolicy Bypass -File build.ps1 -NoReobf

# one module only
powershell -ExecutionPolicy Bypass -File build.ps1 -Stage main -NoReobf

# build + verify + deploy to the instance + launch + report first failure
powershell -ExecutionPolicy Bypass -File cycle.ps1

# launch only (assumes the jar is already deployed)
powershell -ExecutionPolicy Bypass -File launch-test.ps1 -TimeoutSec 175
```

Python for the tools: `C:\Users\moye136\AppData\Local\Programs\Python\Python312\python.exe`
JDKs: `D:\JAVA\java25` (build + run), `D:\JAVA\Java17` (javap, FART).

## 3. The toolchain, and why it is not Gradle

ForgeGradle 2.3 is the only resolver for 1.10.2 Forge; it needs Gradle 4.x; that needs JDK 8. The
upstream sources need javac 17+ (records, `var`, pattern switches). Those cannot be the same JDK, so
`build.ps1` compiles directly against the deobfuscated Forge jar and reobfuscates with
ForgeAutoRenamingTool. Same two steps ForgeGradle performs, just visible.

Hard-won details, each of which breaks the build if ignored:

| Step | Why | Where |
| --- | --- | --- |
| `forgeBin-at.jar` | the compile jar is the *pre-AT* artifact; AT flags must be applied first | `tools/apply_at.py` |
| InnerClasses patch | javac reads nested-class flags from the enclosing class attribute, not the nested class file | `tools/patch_inner_classes.py` |
| Lombok | hundreds of `@Getter`/`@Setter` accessors; without it every one is "cannot find symbol" | `-processorpath libs/lombok-*.jar` + `-proc:full` |
| Mixin AP | generates the SRG refmap; without it every `@Shadow`/`@Inject` fails to resolve | `libs/mixin-processor.jar`, main stage only |
| class version 61 | FART 0.1.22's ASM rejects class file 65 | `tools/lower_class_version.py` |
| selective fastutil | whole fastutil 8 is 23 MB / 13000 classes; only ~2400 are reachable | `tools/stage_fastutil.py` |
| AT into META-INF | FML only reads the AT from `META-INF`, named by the `FMLAT` manifest entry | `build.ps1` |

## 4. Runtime facts that are not obvious

- The user's instance launches on **Java 25 with RetroFuturaBootstrap and LWJGL 3.3.3** (the
  `lwjgl3ify` 1.10.2 port, already installed in that instance's `mods/`).
- Therefore **LWJGL 2 call sites are kept**. `lwjgl3ify` ships a complete `org.lwjglx` layer
  (`Display`, `ContextCapabilities`, `DrawableGL`, `ContextGL`, all of `org.lwjgl.util.glu`), and
  the port compiles against `libs/lwjgl3ify-compat.jar`, i.e. exactly what runs. Upstream's own
  `modules/lwjgl2` backend is therefore **not built**.
- `libs/` must contain the **game's own library versions**: guava 17.0, gson 2.2.4,
  commons-lang3 3.3.2, commons-io 2.4, log4j 2.0-beta9. Using newer versions compiles fine and
  crashes at launch (`NoSuchMethodError: Logger.debug(String, Object)`).
- fastutil is the exception: 1.10.2 ships 7.0.12 but the renderer uses 8.x APIs, so a staged subset
  of 8.5.18 travels inside the jar.

## 5. Current state (authoritative at hand-off)

**Build: complete.** All 7 modules, 1853 sources, **0 errors**:

```
mccompat 4 | gtnhlib 95 | glsm 296 | celeritas 330 | shader 507 | modapistubs 11 | main 610
```

**Jar: valid.** `build/libs/Protactinium-alpha-0.0.1-1.10.2.jar`, 9.43 MB, 5392 classes. Passes
`verify_jar.py` (manifest, `META-INF/protactinium_at.cfg`, refmap, coremod class, zip integrity)
and `verify_mixins.py` (all 123 mixin classes in the configs exist in the jar).

**Runtime: reaches mixin application, does not yet reach the title screen.** It gets through FML
coremod discovery, plugin loading, early-loader registration, mixin config parsing, refmap loading.
It stops on the next member reference written against 1.12.2.

## 6. What is left, exactly

### 6a. Five injection targets with wrong descriptors (`check_injection_targets.py`)

| Mixin | Declared | Reality |
| --- | --- | --- |
| `MixinTileEntityChestRenderer` | `TileEntityChestRenderer.bindTexture` @At INVOKE | `bindTexture` is declared on `TileEntitySpecialRenderer`, so the INVOKE owner is wrong: retarget to `Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;bindTexture(...)V` |
| `MixinTileEntityEnderChestRenderer` | same | same fix |
| `MixinTileEntitySkullRenderer` | same | same fix |
| `MixinTileEntityRendererDispatcherTesr` | `TileEntitySpecialRenderer.render(...DDDFI)` | method is `renderTileEntityAt` |
| `MixinClientChunkManager` | `Chunk.onUnload()V` | 1.10.2 is `Chunk.onChunkUnload()V` |

### 6b. `find_unmapped_mixins.py` reports 39, but it over-reports

Read this before spending time on that list. A mixin needs a refmap entry **only if it references
Minecraft members by MCP name that must be obfuscated**. Many of the 39 have nothing to remap:

- `MixinDirection`, `MixinFrustum`, `MixinTextureUtil` use `@Overwrite` only.
- `MixinWorldSkyColor` injects into a **Forge** method (`ForgeHooksClient.getSkyBlendColour`), which
  is never obfuscated.
- Several already declare SRG names directly.

The reliable test is a launch, not the tool. The tool is a screening aid.

### 6c. The loop for each remaining mixin

1. Read the mixin: `@Mixin` target, every `@Inject`/`@Redirect`/`@At`/`@Shadow`/`@Overwrite`.
2. Get the truth: `& 'D:\JAVA\Java17\bin\javap.exe' -p -classpath build\forgeBin-at.jar <class>`
3. Fix the name/descriptor. If the member has no 1.10.2 equivalent, delete that injection with a
   comment (that is what was done for the toast GUI, which arrived in 1.11).
4. For a **Forge-only** member, add `remap = false` to that injection rather than renaming it.
5. Rebuild, then `cycle.ps1`.

## 7. Already fixed, so do not redo it

| Symptom | Cause | Fix |
| --- | --- | --- |
| `@Shadow field buffer was not located in Tessellator` | 1.12.2 name `buffer`, 1.10.2 `worldRenderer` | declared as `field_178183_a` (the obfuscated name; the AP emits no entry for it) |
| `SoundHandler.setListener` injection failure | 1.12.2 `Entity`, 1.10.2 `EntityPlayer` | descriptor corrected |
| `ActiveRenderInfo.updateRenderInfo` | same | corrected |
| `EntityRenderer.renderCloudsCheck` | 1.12.2 has 6 params | corrected to `(RenderGlobal, float, int)` |
| TESR hooks | 1.12.2 `render(...DDDFIF)` | `renderTileEntityAt(...DDDFI)` |
| toast GUI injections | package arrived in 1.11 | removed from `MinecraftGlDebugMixin` |
| `Logger.debug(String, Object)` at launch | compiled against log4j 2.24 | compile against the game's 2.0-beta9 |
| `ClassCastException ... Adapter cannot be cast to Comparable` | compile-only stubs were packaged | `modapistubs` excluded from packaging |
| `NoClassDefFoundError org/antlr/v4/runtime/CharStream` | coremod runs before the mod class path | antlr/glsl/jcpp embedded in the jar body |
| ~500 phantom "cannot find symbol" | one enter-phase error disabled Lombok | the duplicate `VertexBuffer` imports |

## 8. Decisions the user made, which must be respected

- **SDL and Vulkan: out of scope.** Three entry points remain as inert stubs
  (`SdlGpuBootstrap`, `SdlFrameLifecycle`, `SdlGpuHostTransformer`) so nine call sites stay identical
  to upstream.
- **Distant Horizons: fake it as present.** Shader packs that require DH must load and its options
  must toggle without erroring, but nothing renders and nothing throws. Implemented in
  `net.coderbot.iris.compat.dh.DHCompat`: `isDistantHorizonsLoaded()` returns true,
  `hasRenderingEnabled()` returns true so the `DISTANT_HORIZONS` GLSL macro is defined, depth
  textures return -1 (the value upstream gives before DH's first frame). Disable with
  `-Dprotactinium.iris.fakeDistantHorizons=false`.
- **Iris/shader code: not the focus, but leave the road open.** The `shader` module compiles but is
  never started. Do not delete it; the goal is that it *could* be finished later.
- **Missing 1.10.2 API: back-implement it, do not delete the feature.** Policy in `COMPAT.md`.

## 9. Things that will waste your time if you do not know them

- `net.minecraft.client.renderer.VertexBuffer` (1.10.2) **is** 1.12.2's `BufferBuilder`.
  `net.minecraft.client.renderer.vertex.VertexBuffer` is a **different** class, the VBO wrapper.
  Never import both into one file; qualify one inline.
- Editing sources with PowerShell `Set-Content -Encoding UTF8` writes a **BOM**, which breaks javac
  and Mixin's JSON parser. Use `tools/SourceText.ps1`.
- A broad regex rename is dangerous here: `getPath()`/`getNamespace()` also exist on this project's
  own `OptionIdentifier`/`NamespacedData` types, and `isEmpty()` on plain `Deque`s. Always audit with
  `tools/audit_accessor.py` after a bulk rename.
- Do not launch a jar you have not just built and deployed. Three failures in this session were
  caused by testing a stale jar; `cycle.ps1` exists because of that.
- `chcp`/console encoding mangles non-ASCII in tool output. Prefer reading files with the `read`
  tool and running Python tools over piping javac output through PowerShell.

## 10. File map

```
protactinium-dev/
  build.ps1              the whole build
  cycle.ps1              build + verify + deploy + launch + first-failure report
  launch-test.ps1        launch only, using the instance's own profile JSON
  upstream/              pristine Actinium clone (1.12.2), for diffing
  modules/mc-compat/     back-implemented host API (ItemStack empty, entity brightness)
  modules/mod-api-stubs/ compile-time declarations of mod APIs; NEVER packaged
  modules/gtnhlib/       ported GTNHLib
  modules/glsm/          ported glsm (+ its lwjglCommon/lwjgl3 source roots)
  modules/lwjgl2/        upstream LWJGL2 backend, kept but not built
  modules/celeritas/     ported chunk renderer
  modules/shader/        ported Iris pipeline, compiled but not started
  modules/mod-compat/    per-mod integrations, optional, not built by default
  src/main/java/         ported mod sources
  src/main/resources/    AT, mixin configs, mcmod.info
  libs/                  compile dependencies (game-version-pinned)
  tools/                 20 verification and repair scripts
  build/libs/*.jar       the deliverable
```

Docs: `README.md` (what/why), `COMPAT.md` (compatibility policy),
`PORT_STATUS.md` (per-module + feature state), `LESSONS.md` (eleven mistakes and mechanisms),
`RUN_REPORT.md` (runtime diagnosis, partly superseded), `REMAINING_WORK.md` (the task list),
`DELIVERY_SUMMARY.md` (what was delivered).
