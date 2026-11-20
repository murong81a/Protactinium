# Compatibility policy for Protactinium

Actinium targets Minecraft 1.12.2 on Cleanroom. This port targets Minecraft 1.10.2 on Forge
12.18.3.2511. The two host versions differ in ways that are not cosmetic, and the port has to
choose, case by case, what to do about each difference.

## The rule

**Missing host API is supplied by this port, not worked around and not deleted.**

This is the same decision the `lwjgl3ify-1102` project made for LWJGL: rather than rewrite every
call site to speak the newer library, it ships an `org.lwjglx` shim that gives 1.12.2-era code the
API it expects and implements that API on top of the LWJGL 3 runtime. The shim is small, it is
testable in isolation, and it keeps the ported code readable next to upstream.

Applied here, that means:

| Situation | What this port does | What it does not do |
| --- | --- | --- |
| A field or method is `private` in 1.10.2 but `public` in 1.12.2 | Widen it in `actinium_at.cfg` (the runtime applies the same file through `FMLAT`) | Copy the game class and patch it |
| A method was added in 1.11+ and 1.10.2 has an equivalent under a different name | Add a named wrapper in `com.dhj.actinium.compat.mc` and call that | Delete the feature |
| A method was added in 1.11+ with no 1.10.2 equivalent, but its inputs exist | Implement it in `com.dhj.actinium.compat.mc` from the inputs | Return a constant and hope |
| A whole feature only exists in later versions (a mod that has no 1.10.2 build) | Gate it behind a presence check; the code stays compiled | Delete the integration |

The one thing that is genuinely removed is code that cannot be expressed at all on 1.10.2, and
each such removal is documented in the file that used it with the reason.

## Why not the alternatives

**Deleting the feature.** A deleted feature is invisible: the next session reads the tree, sees no
error, and assumes the behaviour is there. This project already has a rule against leaving a wrong
implementation in the tree, and a silently missing one is the same failure from the other side.

**Forking the game class.** 1.10.2 ships obfuscated; a copied `RenderGlobal` would have to be kept
in sync with every Forge patch and would break every other mod's mixins into the original.

**Compiling two variants.** Maintaining a "1.10.2 compatible subset" next to a "full" build doubles
the surface that has to be kept working and there is no machine here that runs the full one.

## Where the shims live

```
modules/mc-compat/src/main/java/net/minecraft/...      added-to-1.11 API, back-implemented
modules/glsm/src/main/java/com/gtnewhorizons/...       LWJGL 2 constants LWJGL 3 dropped
libs/lwjgl3ify-compat.jar                              the org.lwjglx LWJGL 2 shim (used as-is)
```

`modules/mc-compat` is compiled as the `mccompat` stage, before every other module, and is packaged
inside the mod jar. It is not a library for other mods; it is the private bridge that lets the
1.12.2 sources compile and run unchanged.

## What is actually back-implemented

Each entry is a real 1.10.2 implementation, not a stub. The list is short on purpose.

| Symbol | Why it does not exist in 1.10.2 | What the shim does |
| --- | --- | --- |
| `ItemStack.isEmpty()` | Added in 1.11 alongside the empty-stack singleton | Returns `getItem() == null`, which is exactly 1.10.2's definition of an empty stack |
| `Entity.getBrightnessForRender()` | 1.11 added the no-argument overload | Forwards to `getBrightnessForRender(float)`, 1.10.2's partial-tick form, with the current partial tick |

Entries are added only when a compile error proves the need, so this table stays a record of what
the port actually required rather than a speculative API.
