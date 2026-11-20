# Third-party API declarations used at compile time only

Some ported code names types from mods that have no 1.10.2 build. Two different situations produce
these references, and they need different answers.

## Integrations that were ported to a separate module

`modules/mod-compat` holds the per-mod integrations whose whole body is written against one mod's
API: Lumenized, LittleTiles, BetterFoliage, iChunUtil, StorageDrawers, VoxelMap, CTM, Fluidlogged
API, Depths Update, ModernSplash, NeoFontRenderer, Obscuria's tooltips, CoFH Core, RevoUI and so
on. Those integrations are optional by nature and are compiled only when such a module is asked
for. Nothing in the core renderer depends on them.

## API surface that core code names but does not own

A handful of core files do more than call an integration: they implement or extend a mod's type.
The three that matter are

| Core file | What it needs | Why it cannot be avoided |
| --- | --- | --- |
| `ActiniumBlockAccess` | `FluidloggedBlockAccess` | It *is* an intersection type: `extends IBlockAccess, FluidloggedBlockAccess` |
| `WorldSlice` | `FluidState`, `DepthsUpdateCompat`, `FluidloggedCompat` | Stores fluid states and extends the world height range |
| `ClonedChunkSection` | `FluidStateStorage`, `DepthsUpdateCompat` | Copies per-section fluid state |

Rewriting those to avoid the types would mean removing the fluid-state and extended-height
features from the chunk pipeline, which is a real capability loss rather than a compatibility
detail. So the small part of each third-party API that the core pipeline actually names is
declared here, under the original package names, and compiled into a separate
`mod-api-stubs` module.

## Why declaring them is honest rather than a lie

These files are **compile-time declarations only**:

- they are never packaged into the shipped jar;
- they are only loaded if the real mod is present, because every call site is behind a
  `FluidloggedCompat.IS_LOADED` or `DepthsUpdateCompat`-style presence check that reads the mod
  list, and those checks are false on a 1.10.2 client;
- each declaration carries only the members the ported code names, so nothing can silently call
  into behaviour that does not exist. A missing member is a compile error here, not a runtime
  surprise.

That is the same shape as the LWJGL situation: the port supplies the interface a caller expects
and does not pretend the implementation behind it runs.
