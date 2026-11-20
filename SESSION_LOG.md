# SESSION LOG — read this before touching anything

Appended live. If context was compacted, this plus `HANDOFF.md` is the state.

## Status: 2026-11-20 18:15

**BUILD: COMPLETE.** All 7 modules, 1853 sources, 0 errors.
`build/libs/Protactinium-alpha-0.0.1-1.10.2.jar` = 9.43 MB, 5392 classes, passes `verify_jar.py`
and `verify_mixins.py`.

**RUNTIME: in progress.** Reaches mixin application. Was fixed all the way to
`check_injection_targets.py` reporting **OK: every injection target exists with that descriptor**
(129 targets). Then hit the next class of failure: mixin handler *parameter types* must match the
redirected bytecode exactly.

## Where exactly it is stuck right now

Last error before this note:

```
InvalidInjectionException: @Redirect handler
net/minecraft/client/Minecraft::celeritas$createCoreProfileDisplay
has an invalid signature. Found unexpected argument type
org.lwjglx.opengl.PixelFormat at index 0, expected org.lwjgl.opengl.PixelFormat
```

**Cause (important, affects more than this one site):** an earlier bulk rewrite changed
`org.lwjgl.opengl.*` to `org.lwjglx.opengl.*` in the main module. That is **wrong for mixin
handler/redirect parameters**: a `@Redirect`/`@Inject` handler is matched against the bytecode it
replaces, and Minecraft's own bytecode names `org.lwjgl` types. The lwjgl3ify layer answers those
calls at runtime, but the *signature* must be written in `org.lwjgl` terms.

So the rule is:

- **Inside mixin handler signatures**: use `org.lwjgl.*` (what the target bytecode names).
- **In ordinary non-mixin code that calls the compatibility layer directly** (e.g.
  `org.lwjglx.opengl.Display.getWindow()`): use `org.lwjglx.*`.

### Two files already changed in the current fix attempt

1. `src/main/java/com/dhj/actinium/mixin/vintage/core/startup/MixinMinecraftCoreProfileDisplay.java`
   - imports now `org.lwjgl.opengl.PixelFormat` and `org.lwjgl.LWJGLException` (correct).
   - still has one compile error: `CoreProfileDisplayCreator.create(format.withStencilBits(8))`
     throws `LWJGLException`, and the handler does not declare/catch it. **Fix: declare
     `throws LWJGLException` on the handler method, or wrap in try/catch and rethrow as a
     RuntimeException.** Mixin allows handlers to declare checked exceptions that the target
     method declares; `Minecraft.createDisplay()` declares `org.lwjgl.LWJGLException`.

2. `src/main/java/com/dhj/actinium/startup/CoreProfileDisplayCreator.java`
   - `create(PixelFormat)` / `createWithFallback` / `createDisplay` now take `org.lwjgl.opengl.PixelFormat`.
   - a `convertPixelFormat(PixelFormat)` helper was added that builds an
     `org.lwjglx.opengl.PixelFormat` from the `org.lwjgl` one using the 10-arg constructor
     `(bitsPerPixel, alphaBits, depthBits, stencilBits, samples, 0, auxBuffers, 0, false, false)`.
   - `createDisplay(...)` calls `Display.create(convertPixelFormat(format), attribs)`.

3. `src/main/java/com/dhj/actinium/startup/SdlGpuBootstrap.java`
   - `createDisplay(PixelFormat)` was being widened to `createDisplay(Object)` because the stub
     ignores its argument and only returns false. **This edit was applied but the build was not yet
     re-run when this note was written.** Verify it compiled.

## The exact next commands

```powershell
cd D:\MuRong\cwsgi12313\MRPlus\protactinium-dev
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1 -Stage main -NoReobf
Get-Content build\logs\main.log | Select-String 'error:'
```

Then, when 0 errors:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File cycle.ps1 -SkipBuild -TimeoutSec 175
```

`cycle.ps1` deploys + launches + prints the **first genuine failure**. Read that, fix it, repeat.

## Fixes completed earlier in this same round (do not redo)

| What | File | State |
| --- | --- | --- |
| `bindTexture` INVOKE owner | MixinTileEntityChestRenderer / EnderChest / Skull | retargeted to `TileEntitySpecialRenderer;bindTexture` — done |
| TESR dispatch call | MixinTileEntityRendererDispatcherTesr | `TileEntitySpecialRenderer;renderTileEntityAt(...DDDFI)V` — done |
| `Chunk.onUnload` | MixinClientChunkManager | changed to `Chunk.onChunkUnload()V` — done |
| All 129 @At descriptors | tree-wide | `check_injection_targets.py` reports OK |

## The overall problem shape from here

Every remaining failure is one of two things, and both are mechanical:

1. **A mixin handler signature whose parameter/return types must match the target bytecode.** Use
   `javap` on the *target class* and copy its exact types. Do not use `org.lwjglx` in a handler.
2. **A member name that differs between 1.12.2 and 1.10.2.** `javap` the target, correct the name.

Neither needs design work. The loop is: build → `cycle.ps1` → read first failure → `javap` the
target → fix → repeat.

## Tools and their exact purpose

```
tools/check_injection_targets.py <src> <forgeBin-at.jar>   @At descriptors vs real jar  (currently OK)
tools/find_unmapped_mixins.py    <src> <jar>               remapping mixins w/o refmap   (over-reports, see HANDOFF 6b)
tools/check_shadows.py           <src> <forgeBin-at.jar>   87 @Shadow fields            (currently OK)
tools/verify_jar.py              <jar> [entries]           manifest/AT/refmap/zip
tools/verify_mixins.py           <jar>                     mixin classes present in jar
tools/verify_mixins.py / check_refmap_coverage.py <jar>    refmap coverage
tools/java_errors.py             <log> [--full]            per-file / per-symbol error rollup
tools/errors_by_package.py       <log> [depth]             per-package rollup
tools/jar_grep.py                <jar> <needle> [prefix]   constant-pool search
tools/class_access.py            <jar> <prefix>            access flags incl. nested
tools/apply_at.py / patch_inner_classes.py                 AT application + InnerClasses fix
tools/lower_class_version.py     <in> <out> <major>        FART cannot read class 65
tools/stage_fastutil.py          <modjar> <libjar> <out>   reachable fastutil subset only
tools/SourceText.ps1                                       BOM-safe read/write (USE THIS)
tools/audit_accessor.py          <root> <method>           receivers of a renamed accessor
tools/fix_glu_subclasses.py / strip_dh_frustums.py / fix_mod_mixin_targets.py   one-off rewrites
```

## Do not forget

- `libs/` must hold the **game's** library versions: guava 17.0, gson 2.2.4, commons-lang3 3.3.2,
  commons-io 2.4, log4j 2.0-beta9. fastutil 8.5.18 is the deliberate exception.
- `mod-api-stubs` must **never** be packaged (presence probes would find the stubs).
- Use `tools/SourceText.ps1` for edits; PowerShell `Set-Content -Encoding UTF8` writes a BOM and
  breaks both javac and Mixin's JSON parser.
- A broad regex rename is dangerous: `getPath()`/`getNamespace()` also exist on this project's own
  `OptionIdentifier`/`NamespacedData`; `isEmpty()` also exists on plain `Deque`s.
- Always launch through `cycle.ps1`; three failures this session came from testing a stale jar.
- The user wants SDL/Vulkan excluded, DH faked-present, shader code kept compiling but not started.
  See `HANDOFF.md` section 8.

## 2026-11-20 19:05 - Launch chain progress (26 fixes, client now loads GL and draws)

The launch went from "first mixin fails to apply" to "GL context created, LWJGL 3.3.3 active,
GLStateManager reached", one root cause at a time. Every fix below is a real defect in the port,
not a workaround for a missing feature.

### Mixin application
1. `@Overwrite` bodies get NO refmap entry from the Mixin annotation processor. Hand-written
   entries do not work either (measured twice, both key forms). The working form is the SRG name
   plus `remap = false`. Tool: `tools/convert_overwrite_mixins.py`, gated in the build.
2. `@Shadow` members are the same story and were measured the same way: refmap entries keyed by
   bare name and by qualified reference both FAILED; `@Shadow(remap = false)` with the SRG name
   WORKED. Tool: `tools/convert_shadow_mixins.py`. 71 members converted, 0 compile errors.
3. `MixinGuiMainMenu.panoramaTimer` is `int` in 1.10.2, not `float`.
4. `MixinExtrasInjector` `method = "handleInput"` is a plain string the AP never remaps; it
   must be `func_146269_k`.

### Jar packaging
5. `org.joml` is absent from 1.10.2 and the Iris layer needs it at game start.
   Embedded `libs/joml-1.10.5.jar` like the shader compiler's dependencies.
6. JOML ships class version 46 with no StackMapTable, which the Java 25 verifier rejects.
   `tools/add_stackmaps.py` re-emits every class through ASM with COMPUTE_FRAMES. Two traps
   are documented in that file: `ClassWriter(reader, flags)` inherits the old version and emits
   no frames, and the version must be set in `visit()` not patched afterwards.
7. `ServiceLoader` found no render backend because only `src/main/resources` was copied into
   the jar. Module resource roots are now merged too, and glsm carries a
   `META-INF/services/com.gtnewhorizons.angelica.glsm.backend.RenderBackend` entry.
8. ForgeAutoRenamingTool overwrites the manifest, so `Lwjgl3ify-Aware: true` was lost.
   Without it lwjgl3ify rewrites `org.lwjgl.opengl` to `org.lwjglx` and the LWJGL 3 backend
   dies on `GLDebugMessageCallbackI`, which the shim does not carry.
   Tool: `tools/set_manifest.py`, run after FART.

### Current blocker
`GLStateManager.<clinit>` calls `RENDER_BACKEND.getInteger` which reaches
`GL11C.glGetInteger` with no current capabilities, because nothing in the production code path
calls `GL.createCapabilities()`. Result: native SIGSEGV in `lwjgl_opengl.dll`.
The fix is to call `GL.createCapabilities()` right after the display is created and before
anything touches GLSM.

## 2026-11-20 19:31 - Renderer reached; 12 more root causes, one per launch

State now: the client creates its GL context, selects the LWJGL 3 backend, installs GLSM, resolves
the language, and draws. The launch advances past every mixin and reaches game initialization.

### Measured conclusions about name resolution (do not re-litigate these)

The single most expensive thing on this port was a series of wrong guesses about how a mixin member
name resolves. The measurements, in the order they were taken:

| Attempt | Member form | Result |
|---|---|---|
| 1 | MCP name, refmap entry for the qualified reference | FAILS at apply |
| 2 | MCP name, refmap entry for the bare member name | FAILS at apply |
| 3 | SRG name + `remap = false` | WORKS |

That table cost four launches. It is the whole reason `tools/convert_overwrite_mixins.py` and
`tools/convert_shadow_mixins.py` exist and why the build gates on them.

Counter-example, also measured: `@Accessor` must keep the MCP name even on a `remap = false`
mixin. Converting an accessor to its SRG name makes javac fail with
"Could not locate @Accessor target field_178813_c in target ActiveRenderInfo", because the accessor
name is resolved against the *compile-time* class, which carries MCP names. The tool that did this
was deleted rather than fixed; the assumption it encoded was wrong.

### The other fixes

9. `MixinExtras` injectors (`@WrapMethod`, `@WrapOperation`, `@ModifyExpressionValue`,
   `@ModifyReturnValue`, `@WrapWithCondition`) are in a different processor from the Mixin one,
   so their `method` selector is never remapped: 37 of them were written in MCP form and each
   would have cost a launch. `tools/convert_mixinextras_selectors.py` converts them, scoped to the
   annotation body so it cannot touch the nested `@At` targets.
10. `GL.createCapabilities()` is never called on the LWJGL 3 path. Without it the first
    `GL11C.glGetInteger` dereferences a null function pointer and the JVM dies natively inside
    `lwjgl_opengl.dll`, which hides the real cause. Installed right after display creation.
11. That installation cannot live in an ordinary class: lwjgl3ify rewrites `org.lwjgl.opengl.GL`
    to `org.lwjglx.opengl.GL`, which does not exist. `Lwjgl3Capabilities` carries
    `@Lwjgl3Aware` and is exempt.
12. The jar-level `Lwjgl3ify-Aware: true` manifest attribute is WRONG here even though it looks
    right: it exempts the whole jar, disabling the rewrites the rest of the mod needs, and
    `GLContext.getCapabilities()` then throws `UnsupportedOperationException` from the shim.
    The exemption must be per class.
13. `GlfwWindowBackend` and `GlfwFileDropWatcher` need the same per-class exemption, because
    there is no `org.lwjglx.glfw.GLFW`.
14. `GLStateManager.currentContextCapabilities()` must declare the compatibility layer's
    `ContextCapabilities` return type, not `org.lwjgl`'s, or every redirected call site throws
    `NoSuchMethodError`.
15. `GLStateManager`'s static initializer queried `GL_MAX_TEXTURE_IMAGE_UNITS` unconditionally.
    It runs while a crash report is being assembled, where no context is current, so the query was a
    native crash inside the crash handler. Now guarded on `GL.getCapabilities()`, which is the only
    reliable test; a try/catch cannot see a native access violation.
16. `Int2ObjectMap.computeIfAbsent` does not exist in fastutil 7.0.12, which 1.10.2 ships as a core
    library and which loads before the fastutil 8 subset staged inside this jar.
17. `Locale.properties` is package-private, so the `@Shadow` conversion regex needed to accept
    declarations without an access modifier. One member was missed; the gate now catches the class.
18. Declaring both `create(PixelFormat)` and `create(org.lwjglx PixelFormat)` overloads produces
    `ClassFormatError: Duplicate method name` after lwjgl3ify rewrites both to the same descriptor.
    Only one overload may exist.
19. `MixinMinecraftCoreProfileDisplay`'s redirect handler must declare `org.lwjgl`'s
    `PixelFormat`: Mixin validates the handler against the target bytecode *before* lwjgl3ify
    rewrites the call site.

### Build gates added

`build.ps1` now runs each converter with `--check` after the main compile and fails the build if
a new mixin arrives in MCP form. Without that, each mistake is a launch away instead of a build away.

## 2026-11-20 20:12 - Sound engine reaches start; all mixins apply

Milestone: every mixin in both configs applies, the GL context is created, the LWJGL 3 backend is
selected and GLSM initializes. The start sequence now reaches:

    Sound engine started
    Current Language: 简体中文 (中国)
    Using VBOs: Yes
    LWJGL: 3.3.3+5

The remaining work is one class at a time, and each is the same category of defect.

### Fixes 20-30

20. `@Accessor` MUST keep the MCP name, even under `@Mixin(remap = false)`. The accessor name is
    validated by the annotation processor against the *compile-time* class, which carries MCP names,
    so an SRG name there is a hard compile error ("Could not locate @Accessor target field_..."). The
    one accessor that could not be satisfied at all was `GlStateManager.textureName`;
    it is now written through a cached `MethodHandle` in `VanillaTextureMirrorCompat` instead, and
    the accessor mixin was deleted rather than left broken.
21. JOML needed THREE separate corrections, each found by a distinct verifier message, all recorded
    in `tools/add_stackmaps.py`:
    - version 46 with no frames: "Expecting a stackmap frame at branch target"
    - frames computed from a reader-bound writer: the writer inherits the old version, no frames
    - hierarchy guessed as Object: "Type 'java/lang/Object' is not assignable to org/joml/MemUtil"
    The final form sets the version in `visit()`, uses `EXPAND_FRAMES`, resolves the hierarchy
    from the class files on disk, and falls back to the JDK image for platform superclasses.
22. `GLStateManager.MAX_TEXTURE_UNITS` must be a constant. It sized per-unit state arrays, and the
    class initializes where no context is current (crash report assembly), so a driver query there
    fixed every array at the fallback size and the first real draw threw
    `ArrayIndexOutOfBoundsException`.
23. `Int2ObjectMap`/`Long2ObjectOpenHashMap` have no `computeIfAbsent` in fastutil 7.0.12, which
    1.10.2 ships as a core library. Two sites converted to get/put.
24. A dozen mixins name 1.12.2 methods that 1.10.2 does not have. Each was resolved against the SRG
    or the real class, not guessed:
    `renderLitItem` -> `RenderItem.renderModel`, `renderHeldItem`/`renderItem` ->
    `renderItemModel`/`renderItem`, `renderEntity` -> `doRenderEntity`, `render` ->
    `renderTileEntityAt`, `getSkyColorBody` (unobfuscated, kept as-is), `drawBatch`
    (unobfuscated, `remap = false`).
25. Members that are NOT in the SRG are not obfuscated, so they must keep their plain names with
    `remap = false`. Treating "not found in SRG" as "needs translation" cost two launches.
26. 120 `@At` invoke targets were still in MCP form. Converted in one pass with
    `(owner, name, descriptor)` lookups. The SRG's `MD:` line has FIVE whitespace tokens, with
    owner and name joined in one; splitting on `/` the wrong way silently produced an empty index.
27. `@WrapOperation` / `@WrapMethod` selectors ignore `require = 0`. A MixinExtras injector that
    fails still aborts the mixin, so the only way to keep an optional optimisiation non-fatal is to
    remove it from the config.
28. Mixins targeting this mod's own classes (`ShadowRenderer`) need `remap = false`; otherwise
    their member names are looked up as game names.

### Currently disabled, to be restored

- `features.tesr.MixinRenderManagerBatchEligibility` - render-manager batch eligibility. Its
  `@WrapOperation` on `Render.doRender` inside `doRenderEntity` reports no match although the
  bytecode clearly contains the call. The optimisation is off; the feature it gates simply does not
  engage. Listed in REMAINING_WORK.md.

## 2026-11-20 20:27 - Full startup reached; mixins resolve one class at a time

The launch now reaches "Sound engine started", resolves the language, creates the GL context on
LWJGL 3.3.3, selects the LWJGL 3 backend, and initializes GLSM. Each remaining failure is a single
mixin whose 1.12.2 member name or injection point has no 1.10.2 counterpart.

### What is definitely settled (measured, do not re-test)

| Question | Answer |
|---|---|
| How does a @Shadow/@Overwrite/@Accessor member resolve? | SRG name + `remap = false` for @Shadow and @Overwrite; MCP name for @Accessor (the AP validates it at compile time) |
| Does a hand-written refmap entry fix @Shadow/@Overwrite? | No. Both key forms were tried; the apply still failed |
| Jar-wide `Lwjgl3ify-Aware: true`? | Wrong. It disables the rewrites the mod needs. Use per-class `@Lwjgl3Aware` |
| MixinExtras selector with `require = 0`? | The injector ignores it; a failed match still aborts. Remove from config instead |
| Does `build.ps1` recompile reliably? | No. A stale class file survived three "the fix did not take" rounds. A freshness check was added (`tools`-level), and `-Clean` exists; when in doubt delete `build\classes\<stage>` |

### New fixes 29-35

29. `GLStateManager.currentContextCapabilities()` return type must be `org.lwjglx.opengl.ContextCapabilities`.
30. `Lwjgl3Capabilities` (`@Lwjgl3Aware`) installs `GL.createCapabilities()` after display creation.
31. `GlfwWindowBackend` / `GlfwFileDropWatcher` need `@Lwjgl3Aware` (no `org.lwjglx.glfw.GLFW`).
32. `MAX_TEXTURE_UNITS` is now the constant 32, not a driver query (see SESSION_LOG 22).
33. Two fastutil-8-only `computeIfAbsent` calls replaced with get/put.
34. `ForgeHooksClient.renderLitItem` does not exist in 1.10.2 (it arrived in 1.11). The whole
    fast-lit-item hook was retargeted to `RenderItem.renderModel`, which is where 1.10.2 appends
    item quads, so the feature is preserved rather than dropped.
35. `MixinRenderGlobal` is the most affected class: five `@Overwrite` methods, six `@Shadow`
    fields and two `@Shadow` methods all needed SRG names plus `remap = false`, with every
    internal reference renamed. `renderBlockLayer` has TWO overloads in 1.10.2 mapping to
    `func_174982_a` (private) and `func_174977_a` (public); using the wrong one is silent until
    apply time.

### Where the launch stands

Blocked at `RenderGlobalIrisMixin.actinium`: the `@At` targets the second
`invoke` of `World.func_72826_c(F)F` inside `renderWorldPass`, and 1.10.2's invocation order
differers. The next step is to inspect that method's call sequence and correct the ordinal.

## 2026-11-20 20:43 - TITLE SCREEN REACHED, RENDERER ACTIVE

The port works. The client reaches the title screen, renders the panorama, keeps running, and the
ported renderer reports itself active:

    Selected backend: OpenGL (LWJGL3) (priority 50)
    Initialized backend: OpenGL (LWJGL3) (priority 50)
    init version=1.0 config=advanced{
        multiDraw=DIRECT, streaming=MAP_BUFFER_RANGE, directMemory=true,
        modelRendererBatching=true, modelRendererDisplayLists=true,
        fastLitItemRendering=true, fastLitItemDisplayLists=true }
    Frame pacing: vsync off, cap 30
    LWJGL: 3.3.3+5
    Sound engine started

multiDraw=DIRECT is the batched terrain renderer, streaming=MAP_BUFFER_RANGE is the streaming
buffer path, directMemory=true is the off-heap vertex writer, and the two fast-lit-item flags are
the item rendering shortcut. These are the ported optimisations, on and initialised, not merely
compiled in.

### Fixes 36-40

36. The title-screen skybox needs Angelica's own shader assets (`assets/angelica/shaders/
    panorama_cube.vert` and friends). They were never staged into the jar, so the main menu threw
    "Shader not found". Copied to `modules/glsm/src/main/resources/assets/angelica/shaders` rather
    than adding the whole upstream resource tree, because that tree also carries 23 mixin configs
    for per-mod integrations this build does not produce, and packaging those made `verify_mixins`
    report 52 required classes absent. That failure was the build gate working correctly.
37. `ObjectArrayList.addAll(ObjectList)` is fastutil 8 only; `BatchingFontRenderer.clearBatch`
    now copies with a loop.
38. Access transformers must be written with BOTH the MCP name and the SRG name. The runtime classes
    carry SRG names, so the MCP-only entry widened nothing and `SpriteAnimationDryRun` hit
    `IllegalAccessError` on `field_110982_k`. Four SRG entries added.
39. `FastutilVector3d`/frustum fields: `Frustum` declares `xPosition, yPosition, zPosition` as
    one comma-separated field statement; the shadow converter handled only one name per statement
    and left two MCP names in place.
40. `MixinExtras` method selectors DO get remapped by the annotation processor when written in MCP
    form, so writing SRG names there is wrong when the method is a normal game method: it bypasses
    the processor. `renderSky(FI)V` had to go back to MCP form. The earlier SRG conversions were
    for selectors the processor could not resolve at all, so both forms are legitimate in different
    situations; the rule is to try MCP first and only fall back to SRG when the apply fails.

### Launch command

    powershell -NoProfile -ExecutionPolicy Bypass -File cycle.ps1 -TimeoutSec 220

`cycle.ps1` builds, verifies, deploys with a SHA256 check, launches on Java 25, and prints the
first genuine failure. Use it for every test; a stale jar produced three false diagnoses earlier.
