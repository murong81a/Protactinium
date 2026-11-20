# AUDIT BRIEF — Protactinium 1.10.2 port

**Purpose of this document:** hand-off to a stronger reviewer. It states what works, what is broken,
what the failure mechanism is, and — wherever I do not know the answer — says so explicitly instead of
guessing.

**Bottom line:** the port boots, reaches the title screen, draws the panorama at 30 fps, and opens the
options screen. It cannot load a world. The optimization pipeline initializes but **no measurement
exists proving any of it renders anything**. The previous session burned a large budget converting
one crash into the next, and this document exists so that pattern stops.

---

## 1. What demonstrably works

Verified by launch, with the log line that proves it:

| Fact | Evidence in `run/protactinium-launch.log` |
|---|---|
| Launch reaches the title screen | `Sound engine started`, repeated per sound reload |
| LWJGL 3 is the live GL implementation | `LWJGL Version: 3.3.3+5` |
| The GLSM backend is selected and initialized | `Selected backend: OpenGL (LWJGL3) (priority 50)` then `Initialized backend` |
| The renderer reads a non-default configuration | `init version=1.0 config=advanced{multiDraw=DIRECT, streaming=MAP_BUFFER_RANGE, ...}` |
| The frame limiter runs | `Frame pacing: vsync off, cap 30` |
| Language resolves | `Current Language: 简体中文 (中国)` |
| Mixin application succeeds for most mixins | no `FATAL` line in the last run |
| The options screen opens | screenshot attached by the user |

**What those lines do NOT prove** is anything about drawing performance or correctness. See section 4.

---

## 2. Why entering a world crashes — the mechanism

**Status: not fixed.**

### The mechanism, which explains almost every symptom

A mixin that fails to apply does not merely fail. The transformer throws, the **target class fails to
load**, and every later reference to that class throws `NoClassDefFoundError`. The visible crash is
therefore usually in a class that has nothing to do with the broken mixin.

Example, seen repeatedly:

```
java.lang.NoClassDefFoundError: net/minecraft/client/renderer/tileentity/TileEntityChestRenderer
Caused by: MixinApplyError: MixinTileEntityChestRenderer FAILED during APPLY
```

The renderer never had a problem. Its mixin did.

### The specific chain that blocked world entry

Each fixed one at a time, each taking down its target class:

| Mixin | Failure | Now |
|---|---|---|
| `MixinTileEntityPistonRenderer` | `@Redirect` on a 1.12.2 method name | fixed |
| `MixinTileEntityRendererDispatcher*` | handler had an extra `float alpha` parameter (1.12.2 signature) | fixed |
| `MixinRenderGlobal` | five `@Overwrite` methods and six `@Shadow` fields in MCP form | fixed |
| `MixinBlockRendererDispatcher` | `@At` target in MCP form on an obfuscated call site | fixed |
| `MixinTileEntityChestRenderer` / `…EnderChestRenderer` | contradictory name forms, see section 3 | fixed but **not yet launch-verified together** |
| `TileEntityEndPortalRendererIrisMixin` | `@Shadow` on `getPasses` / `getOffset`, which do not exist in 1.10.2 | fixed |
| `TileEntityBeaconRenderer` | `NoClassDefFoundError` at the time of writing; **cause not yet diagnosed** | **open** |

### Honest state of world entry

The last launch still failed, at `TileEntityBeaconRenderer`. After that there are almost certainly more,
because section 5 shows there are **45 selectors** whose 1.12.2 signatures do not exist here, and the
game only ever reports the first.

**I do not know how many more there are beyond that** — see section 6.

---

## 3. The mistake that wasted the most time (read this first)

I repeatedly decided whether a member needs its MCP name or its SRG name by reading
`build/forgeBin-at.jar`.

**That jar is the deobfuscated jar, so it always shows MCP names.** The runtime client carries SRG
names. Any decision made from it is wrong by construction. This single error produced at least four
contradictory revisions of the chest mixins:

1. `renderTileEntityAt` (copied from that jar) → mixin fails
2. `func_180535_a` (from the SRG table) → works
3. back to `renderTileEntityAt` (again from that jar) → fails again

**The rules that are actually correct, all measured:**

| Construct | Which name | Why |
|---|---|---|
| `@Shadow` field or method | **SRG**, with `remap = false` | the annotation processor emits no refmap entry for `@Shadow` in this build |
| `@Overwrite` method | **SRG**, with `remap = false` | the processor emits no entry for an `@Overwrite` body at all |
| Standard injector selector (`@Inject`, `@Redirect`, `@ModifyArg`) | **MCP** | the processor remaps it |
| MixinExtras selector (`@WrapMethod`, `@WrapOperation`, `@ModifyReturnValue`) | **SRG** | used verbatim, never remapped |
| `@Accessor` / `@Invoker` value | **MCP** | validated at compile time against the deobfuscated class; an SRG name is a compile error |
| A member the SRG does not map | **plain name**, `remap = false` | not obfuscated in production |

Hand-written refmap entries do **not** fix `@Shadow` or `@Overwrite`; both key forms were tried and both
failed.

---

## 4. "Most of the optimizations may not exist" — the criticism is correct

I cannot currently refute it, and I should not have implied otherwise.

**Not proven, and previously implied as working:**

1. **`multiDraw=DIRECT` in the log is a configuration field.** It is read at startup. The batched
   terrain renderer only runs when a world renders sections, which has never happened. Printing a
   config value is not evidence that batched draws occur.
2. **No frame time has ever been measured.** No baseline, no A/B, no number, no screenshot comparison.
3. **No self-check is wired.** The sibling project's `AGENTS.md` is explicit that a feature must
   register with a self-check and mark its draw site, because "allocated is not running" — a feature
   once logged itself active for hours while drawing nothing. I did neither here.
4. **The fast-lit-item path was retargeted, not verified.** `ForgeHooksClient.renderLitItem` does not
   exist in 1.10.2 (it arrived in 1.11), so the hook moved to `RenderItem.renderModel`. Whether it
   fires, and whether the output matches vanilla, is untested.
5. **Shader and shadow paths never ran.** Compiled, mixins apply, but they need a world and a pack.

**Correct statement:** the pipeline initializes. Nothing has been shown to render faster, or even
correctly.

**How to settle it, once a world loads:**
increment a counter inside each feature's draw site, log it once per second, and treat a counter that
never moves as proof the optimization is not running. Then A/B frame time in one world, one position,
one settings file, several runs, medians.

---

## 5. The systemic problem: 45 selectors target methods that do not exist here

`tools/audit_injection_selectors.py` resolves every injection selector against the real 1.10.2 classes
without launching. It reports:

```
45 selector problem(s) out of 280 checked
```

Every one is a place where a 1.12.2 signature was carried over. Representative examples:

| Mixin | Selector | Reality in 1.10.2 |
|---|---|---|
| `MixinTileEntityItemStackRendererGuiSupersampling` | `renderByItem(Lnet/minecraft/item/ItemStack;F)V` | `renderByItem(ItemStack)` |
| `MixinTileEntityRendererDispatcherLightmap` | `renderTileEntity(Lnet/minecraft/tileentity/TileEntity;FI)V` | descriptor differs |
| `EntityRendererIrisMixin` | several on `renderWorldPass` / `updateCameraAndRender` | names match, some descriptors do |

**This is the answer to "why does it keep crashing":** the game reports the first broken mixin it
hits. There are 45, and each one crashes its target class. Fixing them one per launch is what consumed
the budget.

**This is also the answer to "are the optimizations there":** the main render-path mixins
(`EntityRendererIrisMixin` alone has dozens of injections on `renderWorldPass`) are among the
unresolved ones, so the world-rendering interception may never be attached at all.

---

## 6. What I do not know

Stated plainly, because guessing has already cost too much:

1. **How many failures remain after `TileEntityBeaconRenderer`.** I know of 45 selector problems; I do
   not know how many of those produce a fatal mixin failure versus a silent skip.
2. **Why the options screen layout overlaps.** The screenshot shows text drawn on top of other text at
   wrong coordinates. Missing translations explain the raw keys but not the overlapping. I have not
   investigated the layout math and do not know the cause.
3. **Whether the Celeritas terrain renderer can run on 1.10.2 at all.** `SimpleWorldRenderer`,
   `RenderSectionManager` and the section build pipeline are large ported subsystems. None of it has
   ever executed.
4. **Whether the TESR batching subsystem is functionally complete.** `TesrBrackets`,
   `TesrProviderDispatch`, `VanillaModelMeshes` are large; I have read them only partially.
5. **Whether the staged fastutil subset is sufficient.** 686 classes are bundled (down from 2381);
   sufficiency is proven only as far as "the title screen loads". A world may need more.
6. **Whether `@Lwjgl3Aware` correctly exempts exactly the right classes.** Four were annotated after
   the corresponding `NoClassDefFoundError`s; there may be more.

---

## 7. Defects I introduced, and their state

1. **`tools/add_stackmaps.py`** rewrites embedded libraries so the Java 25 verifier accepts them. Four
   revisions, each fixing a measured failure:
   - raising the version without frames → `VerifyError: Expecting a stackmap frame`
   - `ClassWriter(reader, flags)` inherits the old version → still no frames
   - hierarchy guessed as `Object` → `Bad return type` on `jetbrains`-style merges
   - `EXPAND_FRAMES` omitted → frames describe the wrong return type

   It now runs over **all** embedded libraries (JOML 46, jcpp 49, antlr 52, glsl-transformer 52), not
   just JOML. The `VerifyError` on `org/anarres/cpp/PreprocessorCommand.forText` — the embedded C
   preprocessor inside `jcpp-1.4.14` — came from this being applied to JOML only. **That specific
   failure has not been re-verified since the change.**

2. **`tools/stage_fastutil.py`** rewritten three times. Bundled fastutil went 2381 → 1275 → 523 → 686
   classes as the closure was tightened and then corrected (a parser bug returned zero supertypes, so
   the first "84 classes" result was invalid). Jar went 10.05 MB → 8.15 MB.

3. **Two mixins are disabled rather than fixed:** `MixinRenderManagerBatchEligibility` was removed from
   `mixins.actinium.iris.json` because its `@WrapOperation` reported no match while the bytecode
   clearly contains the call, and MixinExtras ignores `require = 0`.

4. **`src/main/resources/assets/**/lang` was empty**, so every options label rendered as a raw key.
   12 upstream lang files plus 19 missing `actinium.*` keys were added, and `build.ps1` now asserts
   that every lang file reaches the package. **Not yet verified by a rebuild.**

---

## 8. Build and packaging facts a reviewer needs

- **No Gradle.** ForgeGradle 2.3 needs JDK 8; the sources need javac 17+. `build.ps1` compiles
  directly against the deobfuscated Forge jar and reobfuscates with ForgeAutoRenamingTool + MCP
  stable_29 SRG (`_tmp/ysm/.../mcp-srg.srg`).
- **Runtime:** Java 25 + RetroFuturaBootstrap + LWJGL 3.3.3 via the lwjgl3ify port.
- **Class version:** sources compile at `--release 21` (major 65), then every class is lowered to
  major 61 because FART's embedded ASM rejects 65.
- **A note on stack maps:** an audit found 1783 classes declaring major 61 with no `StackMapTable`. I
  initially reported this as a systemic defect; **that was wrong.** A control experiment
  (`.scratch/frame_probe.py`) shows javac emits frames at every release level, and classes whose
  methods have no branch target legitimately carry none. One suspected class was loaded under
  `-Xverify:all` successfully. Byte-scanning for branch opcodes over-reports and must not be trusted.
- **Gates that run in `build.ps1`** and fail the build: `convert_overwrite_mixins.py`,
  `convert_shadow_mixins.py`, `convert_mixinextras_selectors.py`, `verify_mixins.py`, `verify_jar.py`,
  `check_refmap_coverage.py`, `check_injection_targets.py`, plus stale-output detection.
- **Their weakness:** every one checks *form*, not *behaviour*. All the failures in section 2 passed
  the gates and died at runtime. `audit_injection_selectors.py` is the first gate that checks against
  real classes, and it is what finally quantified the problem at 45 instead of 1-per-launch.

---

## 9. What I would do next, in order

1. Run `tools/audit_injection_selectors.py` as a build gate, then fix all 45 selectors in one pass
   against the SRG table. Not one per launch.
2. Diagnose the `TileEntityBeaconRenderer` failure, then repeat step 1 until a world loads.
3. Add draw-site counters to each optimization, and only then claim anything works.
4. Rebuild and confirm lang files are packaged and the options screen reads correctly.
5. Investigate the options-screen layout overlap (section 6.2).

---

## 10. Reproduction

```
cd D:\MuRong\cwsgi12313\MRPlus\protactinium-dev
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1        # build only
powershell -NoProfile -ExecutionPolicy Bypass -File cycle.ps1 -TimeoutSec 220
powershell -NoProfile -ExecutionPolicy Bypass -File cycle.ps1 -TimeoutSec 220 -AutoloadWorld porttest
```

`cycle.ps1` builds, verifies, deploys with a SHA256 check, launches on Java 25, and prints the first
genuine failure. Always use it; three false diagnoses came from launching a stale jar.

Independent checks a reviewer can run without launching:

```
python tools/audit_injection_selectors.py . build/forgeBin-at.jar <javap> <srg>
python tools/audit_mixin_members.py . <srg>
python tools/check_missing_packages.py build/libs/<jar> build/forgeBin-at.jar
```


---

## 11. Delivery state at commit time

- Jar: `build/libs/Protactinium-alpha-0.0.1-1.10.2.jar`, **8.20 MB** (was 10.05 MB).
- Language files packaged: **16** (were 0, which is why the options screen showed raw keys).
- Pre-version-50 classes without stack map frames: **0**. All embedded libraries (JOML 46, jcpp 49,
  antlr 52, glsl-transformer 52) are re-emitted through ASM, so the `VerifyError` class of failure is
  addressed for every library that was audited.
- fastutil: **1275** classes staged. This is higher than the 686 measured earlier because the staging
  tool was changed again after that measurement. **The regression is not explained in this document.**
  Neither figure is fully trusted: the 686 result came from a parser bug that returned zero
  supertypes, and the 1275 result follows a widening of the closure. This needs re-examination.
- Build: all seven stages compile with 0 errors and every form gate passes.

Checks a reviewer can run without launching:

```
python tools/audit_injection_selectors.py . build/forgeBin-at.jar <javap> <srg>   # 45 problems
python tools/audit_mixin_members.py . <srg>                                       # OK, 53 checked
```
