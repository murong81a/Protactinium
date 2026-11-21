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

## Known unrelated defects

- `IllegalStateException: Already building!` from `ParticleInstancer.renderParticle` when a sweep
  attack particle renders: the instancer starts a second `VertexBuffer` build inside its own.
- Five settings write fields nothing reads: `useFasterClouds`, `useNoErrorGLContext`,
  `enableMemoryTracing`, `useAdvancedStagingBuffers`, `disableIncompatibleModWarnings`.
- The loading screen frame limit does not depend on the max frame rate, so it can be set above it.
