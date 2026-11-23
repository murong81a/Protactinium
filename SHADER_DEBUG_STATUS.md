# Shader debugging status

Started 2026-11-21 while bringing modern shader packs (photon 1.4) up on the 1.10.2 port.
This file records what was proven, not what was assumed; every claim below has a log line or a
dumped source behind it.

## Goal

Modern packs must load **and** render correctly. The port previously refused them at the first
capability check, so "loads" and "renders" had never been separated.

## Fixed today

| Symptom | Cause | Fix |
| --- | --- | --- |
| Every mod translation key shown raw | 1.10.2 loads `lang/en_US.lang`; the port shipped `en_us.lang` | renamed all 12 files to the 1.10.2 casing |
| Magenta GUI widgets, missing icons | the port's resources had no PNGs at all; only `upstream/` had them | copied the 7 icons and `assets/angelica/shaders/` |
| `options.chunks`, `options.clouds.*` shown raw | 1.12.2 keys that 1.10.2 never had | repointed to 1.10.2 keys, added `%s` forms |
| Translatable args never resolved | `TextComponent.Translatable.toString()` appended the argument list to the key | `I18n.format(key, args)` |
| compute / SSBO / image load-store all "unsupported" on a GL 4.6 context | three separate defects, see below | see below |
| GPU chunk culling off (NO_BACKEND_COMPUTE) | same capability chain | reason now MULTI_DRAW_NOT_INDIRECT |

### The capability chain (three defects, in order of discovery)

1. `Lwjgl3Capabilities.install()` called `GL.getCapabilities()` as a guard. LWJGL 3.3 throws
   `IllegalStateException: No GLCapabilities instance set` until capabilities exist, and the throw
   was caught and logged, so capabilities were never created.
2. It was also called before any context existed (`create()` before `createWithFallback()`); moved
   into the loop right after `Display.create`.
3. Even with capabilities installed, the renderer read the flags from
   `org.lwjglx.opengl.ContextCapabilities`, and **javac had inlined those fields as constants**:
   the compile-time type declares `public final boolean OpenGL43 = false`, which is a constant
   variable, so every read compiled to `false`. Reads now go through `CapabilityFlags.of(...)`
   (reflection), and the flags are raised from `GL_VERSION` in `RenderSystem.init()`.

### GPU self test

`GpuSelfTest` runs at startup and performs the real operations, then reads the results back:
an SSBO round trip through a compute shader, and an R32UI image written and read inside a compute
shader. Both pass on this machine:

```
[GPU self test] SSBO and compute dispatch verified: 16 values doubled on the GPU
[GPU self test] image load/store verified: 16 texels written and read back on the GPU
GL capabilities: ... OpenGL43=true compute=true ssbo=true imageLoadStore=true ...
```

A failing test demotes the corresponding flag, so a pack is refused instead of rendering wrong.

### DSA

With OpenGL45 finally reported, the DSA implementation switched from `DSAARB` (the only one ever
exercised) to `DSACore`. It is now a switch, defaulting to ARB:
`-Dactinium.dsa.core=true` selects the core path.

## Ruled out for the wrong photon picture

The picture still folds terrain over the horizon. These were each measured and are not the cause:

| Suspect | Evidence |
| --- | --- |
| DSA implementation | forcing `DSAARB` changes nothing |
| Projection / model view | `[terrain dump main] projection m00=0.8034 m11=1.4294 m22=-1.0006 m23=-1.0`, `modelView translation=(0,0,0.05)` |
| Region offset | `regionOffset=(-61.2, -8.6, -122.2)`, plausible |
| Vertex shader loses the block offset | `celeritas_terrain_*.vertex.glsl` computes `_vert_position + u_RegionOffset + _get_draw_translation(_draw_id)` |
| Terrain bound to the generic attribute program | `celeritas chunk program: pass=gbuffers_terrain handle=180 format=ExtendedChunkVertexType` |
| Vertex format scale | `VanillaLikeChunkVertex` stores float positions; scale 1.0 / offset 0.0 match |

## Current hypothesis

The fold is in the **composite** stage, not in the geometry: sky pixels are being shaded as if they
were terrain, which is what a depth convention mismatch looks like. Next questions:

1. What depth does photon expect (reversed Z, near/far, `depthtex` semantics)?
2. Which texture and format is `depthtex0/1/2` bound to?
3. Does `screen_to_view_space` reconstruct the same world position the gbuffer wrote?

## Diagnostics left in the build

| Switch | Effect |
| --- | --- |
| (always on) `StageTrace` | one `stage trace:` line per second with per-pass execution counts |
| (always on) shader dump | every compiled source written to `shaderdump/`, plus `celeritas_terrain_N.vertex.glsl` |
| (always on) terrain dump | one line per pass with model view, projection and region offset |
| (always on) `celeritas chunk program` | which program the terrain draw binds |

Turn the always-on ones off before shipping: they cost I/O on first use and flood the log.

## Resolved 2026-11-23: entities drawn off the ground

Symptom: with any shader pack, every living entity and the player appeared displaced upward, and
standing still while turning the camera left them pinned to the screen. Without a shader pack the
same scene was correct, and the terrain itself was always correct.

Root cause, proven from the live matrices rather than inferred:

- The per-draw `iris_ModelViewMatrix` upload was correct: the uniform read back byte for byte the
  matrix on GLSM's stack, including the camera rotation, and the refresh ran against the program
  that issued the draw.
- The matrix on the stack at the entity loop was **the camera model view with an extra rotation
  about Z equal to the celestial angle** (about 35 degrees during the measurements). That rotation
  was already present at the shadow pass entry, so it was not the shadow pass and not the entity
  render. A snapshot taken right after the camera transform was clean; the same stack read one
  stage later, after the sky, was tilted.
- Consequence: terrain draws through celeritas, which uploads its own model view, so it stayed
  correct. Entities draw through the OpenGL stack, so they inherited the tilt. The tilt rotates
  the entity positions about the view axis, which is why they appeared to float and why turning
  the camera moved the ground but left the tilt in place.

Vanilla `RenderGlobal.renderSky` rotates the model view by the celestial angle and restores it;
on this version the restore does not reach GLSM. The fix is two model view restores:

1. `RenderGlobalIrisMixin` saves the model view at `renderSky` HEAD and writes it back at RETURN,
   which removes the leak at its source.
2. `EntityRendererIrisMixin` snapshots the camera model view right after the pass setup and writes
   it back before the entity loop. This is the guard that was verified to move entities back onto
   the ground; it stays because any future stage that leaks the stack would otherwise reappear as
   the same symptom.

## Resolved 2026-11-23: sweep attack particle crash

`ParticleInstancer` treated the self-drawing particles as ordinary captured particles, so
`CAPTURE.begin()` had already opened the buffer when the particle called `begin` itself. The
`IndependentParticleDraw` marker existed but nothing implemented it. `ParticleSweepAttack`,
`ParticleFootStep` and `ParticleExplosionLarge` now implement it through three empty mixins, which
routes them through `ParticleRunSplitter.renderIndependent`. Verified present in the jar; no
`Already building!` in ten minutes of play afterwards.

## Resolved 2026-11-23: GL_INVALID_OPERATION (1282) on every composite program use

Symptom: `checkpoint=composite:use-program error=1282` on every fullscreen pass, thousands per
second, starting at world load with shaders and continuing for the whole session. The renderer
still drew, so this was easy to dismiss as log noise; it was not, because the same bind failure
was the sampler path the packs use.

Root cause, named by the driver rather than inferred. With lwjgl3ify's OpenGL debug logger on
(`config/lwjgl3ify.cfg`, `debug.registerOpenGLLogger=true`), the driver reports:

```
GL_INVALID_OPERATION error generated. <texture> is not a valid texture name.
  at GL45C.glBindTextureUnit
  at Lwjgl3SamplerBinding.updateSampler
  at ProgramSamplers.update
  at Program.use
  at CompositeRenderer.renderAll
```

`DHCompat` reported Distant Horizons as present by design (`REPORT_PRESENT = true`). Packs then
declared the `dhDepthTex` samplers and took their DH branches, but no DH renderer exists, so the
depth texture supplier returns `-1`. `glBindTextureUnit` only accepts zero or an existing texture
name, so every use of every program with that sampler raised `GL_INVALID_OPERATION`.

Fix, two layers:

1. `DHCompat` now reports DH absent by default. `-Dprotactinium.iris.fakeDistantHorizons=true`
   restores the facade for a pack that refuses to load without DH. Photon and Bliss both load
   and render without it; the earlier assumption that packs need the facade was wrong for them.
2. `SamplerBinding` never binds a negative texture id; it binds zero instead, which leaves the
   unit unbound and samples as zero. This keeps the facade opt-in harmless.

Measured after the change: 0 `composite:use-program` errors and 0 driver texture errors over a
full world load with Bliss, and the pack loaded normally.

Also fixed in the same session:

- Celeritas bound its terrain program with a direct service `glUseProgram`, invisible to GLSM's
  program cache. `GlProgram.bind()/unbind()` now report the bind through
  `GLSMPrograms.onExternalProgramBind`, so a later cached bind of the same number is not skipped.
- A minimized window reports 0x0 for a frame; `RenderTargets.resizeIfNeeded` resized every render
  target to zero and the next framebuffer build threw `INCOMPLETE_ATTACHMENT`. Non-positive
  sizes are now ignored, and `RenderTarget.resize` keeps the last valid storage.
- Automatic world loading from `-Dprotactinium.autoloadWorld` launched the integrated server from
  `GuiMainMenu.initGui`, before Minecraft's loading screen exists, and threw a NullPointerException.
  It now defers to the first client tick.

Measurement rules from this one:

- The driver's KHR debug messages name the failing call and the reason. Enabling lwjgl3ify's
  OpenGL logger turns a guessed Java-level hunt into a single stack trace; use it before adding
  probes of your own.
- A probe that calls `glGetError` consumes the error, and probes inside the suspect call changed
  whether the flood happened at all, which sent the search in the wrong direction for a while.

## Resolved 2026-11-23: freeze when applying a pack, and iterationT Next shader output

Symptom: pressing Apply on the shader pack screen with `光影包iterationT Next.zip` froze the
client. The process stayed alive at 4.8 GB with the window not responding. `jstack` showed the
client thread waiting in `CompositeRenderer.getTransformed(...).join()` and the transform worker
at 342 s CPU inside `org.taumc.glsl.Transformer.removeUnusedFunctions`, in
`ArrayList.contains`.

Cause, proven by reproducing the call offline on the preprocessed source:

- The pack writes C-style float literals such as `40f` and `1530f`. NVIDIA accepts them; the
  ANTLR GLSL grammar does not.
- The syntax error makes ANTLR's recovery keep a second, prototype-only copy of every function
  inside a `declaration_statement`. The library's `removeFunction` only removes a prototype whose
  grandparent is an external declaration, so those copies are never removed.
- `removeUnusedFunctions` sets `changed = true` for every unused name regardless of whether
  anything was removed, so the fixed-point loop never terminates.

Fixes:

1. `CompatibilityTransformer` no longer calls the library's `removeUnusedFunctions`. It performs
   the same pass while collecting prototypes and identifiers itself, and stops when a pass leaves
   the unused set unchanged. Replayed against the 366 cached preprocessed sources: all terminate.
2. `GlslTransformUtils.normalizeFloatSuffixes` strips the C-style `f` suffix before parsing, so
   the recovery never happens for these sources. All 101 cached iterationT Next sources then
   parse with zero syntax errors.
3. `ShaderTransformer.parseChecked` rejects a recovered parse tree with the stage and error
   counts instead of printing `<missing ';'>` markers and duplicate bodies into the shader, which
   is what the driver had rejected.

With the freeze and the recovered tree gone, two further defects surfaced, both measured:

- The transformed header was built as `profileString + "\n" + extensions`, and `profileString`
  already ends in a newline. The resulting blank line between `#version` and `#extension` makes
  the GLSL pre-parser stop collecting the preamble. Anything that re-parses a transformed shader
  (the opaque terrain pass's alpha discard transformer) then dropped
  `#extension GL_ARB_gpu_shader5 : require`, and NVIDIA refused `fma` below `#version 400`.
  Hoisting the whole shader to 400 instead is wrong: it made `mod(int, float)` ambiguous and broke
  the composite shader. The header now has no blank line, and
  `GlslTransformUtils.enableGpuShader5ForFma` adds the extension for pre-400 sources that use
  `fma` without declaring it.

Measured result: iterationT Next loads in about 2.1 s, all composite and terrain programs compile,
0 `error C` lines, 98 frames at ~11 ms average, world visible behind the pack's settings screen.
The transformed terrain fragment is now dumped next to the vertex source as
`shaderdump/celeritas_terrain_<N>.fragment.glsl`, because the fragment is compiled by the chunk
renderer's shader class and never reached the compile-time dump.

## Verified 2026-11-23: the composite picture is correct

The old "sky black / terrain folded over the horizon" hypothesis does not reproduce. Measured
with Photon v1.4 (TAAU enabled, the shader packs settings file has TAAU=true) in the skytest
world at daytime, light 15:

- the whole composite chain ran: prepare, deferred1..4, composite..composite9, final;
- per-buffer sizes from shaders.properties were applied: colortex4 192x108, colortex6 and
  colortex14 at half resolution (427x240 of 854x480);
- 0 `error C` lines in the driver compile log;
- the sky region (top 35% of a 1706x1066 capture) had mean luminance 134.5 and 0.00% near-black
  pixels, and the screenshot shows a normal blue sky with clouds and terrain below the horizon.

iterationRP Alpha 0.8.9 was also captured at daytime with a correct picture before this run.

Because the defect that the forced-on GL capture was instrumenting is measured gone, the capture
is opt-in again: `IrisGlDebug.isEnabled()` now reads the config switch
(`debug.enableActiniumGlDebug`, default false) or `-Dactinium.glDebug=true`. This removes the
per-second pixel readbacks and the per-event state dumps from normal play.

## Resolved 2026-11-23: packs that use colortex16..31

Replaying a pack switching session found one pack failing pipeline creation:
`Eclipse-Shader-SR-Support-Unstable.zip` threw
`Render target with index 17 is not supported, only 16 render targets are supported.` from
`RenderTargets.setupColorAttachments`. The pack writes its super-resolution motion vectors to
`colortex17` and samples `colortex16`/`colortex18`; Iris 1.10.5 raised the colortex range from 16
to 32, and this pack targets that range.

`IrisLimits.MAX_COLOR_BUFFERS` is now 32. The renderer keeps the historical colortex0..15 range
always allocated and grows only as far as the pack actually references (see below). Measured:
Eclipse allocates 19 targets, including colortex16 RGBA16F, colortex17 RG16F and colortex18 RGBA,
loads in 1.6 s with 0 `error C` lines and renders (sky region mean luminance 158.9, 0%
near-black). SEUS PTGI HRR 3 allocates 16 targets, loads in 2.8 s with 0 errors as a regression
check.

Allocation is on demand above the historical range. `ProgramSet` scans every program source for
`colortexN`, `colorimgN`, `DRAWBUFFERS` and `RENDERTARGETS` references and passes only
`max(16, highest referenced + 1)` targets to the directives. Packs that stay within colortex0..15
therefore keep the old memory cost, and Eclipse allocates 19 targets instead of 32. The scan is
deliberately generous: over-detecting a buffer only allocates one more target, while missing one
would make pipeline creation reject the draw buffer. `MAX_COLOR_BUFFERS=32` is also injected as a
shader define now, so packs that gate features on it see the real capability. `IRIS_VERSION` is
still not injected; claiming a specific Iris generation would enable unverified code paths.

## Backlog

- **114514A** - shader pack compatibility matrix. Load the common and uncommon packs through the
  launcher, collect every compile or runtime failure from the log, and close the gaps one at a
  time. Deferred on 2026-11-23; on-demand render-target memory and the `MAX_COLOR_BUFFERS` define
  were taken first.

## Source review 2026-11-23: dead ends and small gaps

Reviewed the whole tree (1889 Java files) for empty implementations and silent failures. Most
empty methods turned out to be deliberate: Mixin `@Shadow` stubs, record constructors, the SDL
entry points (out of scope), the Distant Horizons facade (DH has no 1.10.2 build), the disabled
flight recorder, `GlImage.updateNewSize` (an upstream hook that intentionally follows no target),
`GbufferPrograms.init` (static initialiser), `IrisCeleritasChunkShaderInterface.setTextureSlot`
(Iris binds samplers itself) and the entity atlas page-lifetime methods. The genuinely incomplete
items were fixed:

- `IrisApiV0Impl.openMainIrisScreenObj` returned `new Object()` behind a `TODO: GUI`. API
  consumers (and Celeritas's own shader screen bridge) expect a Screen, so they had nothing to
  display. It now returns a `ShaderPackScreen` with the parent screen when one is supplied.
- Custom uniform expressions lacked the GLSL vector forms `min(vecN, float)`, `max(vecN, float)`,
  all three `mix` forms, which packs write in shaders.properties. Added with component-wise
  semantics and verified offline: min(vec2(1,5),2) = (1,2), max(vec3(1,5,3),4) = (4,5,4),
  mix(vec2(0,10),vec2(10,0),0.5) = (5,5), mix(vec2(0,10),5,0.5) = (2.5,7.5),
  mix(vec3(0),vec3(1,2,3),vec3(0,0.5,1)) = (0,1,3).
- `Iris.tryReadConfigProperties` and `tryUpdateConfigPropertiesFile` swallowed IOExceptions with
  only a `TODO: Better error handling`; both now log a warning with the path, so a config that
  silently fails to persist is visible.

Not changed, recorded as intentional: the `fantastic/` particle interfaces (`PhasedParticleEngine`,
`WrappingMultiBufferSource`, empty `IrisParticleRenderTypes`) are upstream remnants with no call
sites in this port; particle drawing goes through the Celeritas/Embendium path instead.

Verified after the changes: full build 0 errors; SEUS PTGI HRR 3 loads in 2.5 s with 0 compile
errors, 0 pipeline failures and a correct frame (sky region mean luminance 184.3).

## Measured and rejected 2026-11-23: GPU-driven chunk culling as a default

The compute culling path exists and is selectable through the performance options: chunk multi-draw
mode INDIRECT plus GPU culling mode COMPUTE. Both default to the CPU path (DIRECT + CPU_ONLY).
Measured A/B with the same world, pack and view, after focusing the window and setting
`pauseOnLostFocus:false`:

| arm | frame avg | renderer avg |
| --- | --- | --- |
| DIRECT + CPU_ONLY | 2.69 - 2.80 ms | 2.13 - 2.49 ms |
| INDIRECT + COMPUTE | 2.63 - 2.93 ms | 2.48 - 2.67 ms |

The compute path logged `GPU chunk culling available, mode=COMPUTE` and produced no errors, but the
difference is inside the noise. This scene is fog-limited at roughly 370 fps with few visible
chunks, so culling work is too small to show. Defaults therefore stay CPU-side until a high
render-distance scene is measured.

Measurement trap: the first attempt ran with `pauseOnLostFocus:true`; the window lost focus, the
game paused, and the two arms read 3.4 ms vs 2.7 ms for reasons unrelated to culling. Both
`options.txt` and `config/actinium-options.json` were restored after the test.

Remaining memory follow-up: every render target still allocates a main and an alternate texture
eagerly. Lazy allocation of the alternate would cut render-target memory substantially for packs
that do not ping-pong every buffer, but the flip and feedback paths (`stageWritesToMain`,
`ClearPassCreator`, explicit flips) need an audit first.

## Known unrelated defects

- Five settings write fields nothing reads: `useFasterClouds`, `useNoErrorGLContext`,
  `enableMemoryTracing`, `useAdvancedStagingBuffers`, `disableIncompatibleModWarnings`.
- The loading screen frame limit does not depend on the max frame rate, so it can be set above it.
- The build script used to exit 0 after a compile failure, which let `cycle.ps1` deploy the
  previous jar and produced misleading test runs. It now exits 1; a failed compile stops the cycle.
