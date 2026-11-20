> **Superseded in part.** This report was produced against the 17:33:52 jar. Two of its findings
> have since been actioned: the SoundHandler.setListener descriptor was corrected to take an
> EntityPlayer (that mapping is now present in the refmap), and the GuiToast injections it
> flags were removed. Its central diagnosis, that the Mixin annotation processor silently drops
> entries for targets it cannot resolve, is correct and is now covered by
> `tools/check_refmap_coverage.py` and `tools/find_unmapped_mixins.py`. Treat the log excerpts
> below as evidence about that jar, not about the current build.
# RUN REPORT - Protactinium 1.10.2 runtime diagnosis

Date: 2026-11-20
Author: diagnostician session (no source files were edited)

## 1. Verdict

**FAILURE. The client crashes during class transformation, before the title screen.**

The launch never reaches LWJGL init, the texture atlas, or the sound engine. It dies while
`net.minecraft.client.Minecraft` is being transformed, because a mixin injection does not find
its target.

This is a **mixin injection failure caused by a missing refmap entry**, not a missing class and
not a NoSuchMethodError in mod code.

## 2. What was launched

| Item | Value |
|---|---|
| Launcher | `launch-test.ps1 -TimeoutSec 170` |
| Java | `D:\JAVA\java25\bin\java.exe`, major 25 |
| Instance | `D:\pcl\versions\1.10.2-Forge_12.18.3.2511` |
| Mods present | `!mixinbooter-11.18.jar`, `lwjgl3ify-1.10.2-port-0.0.1.jar`, `Protactinium-alpha-0.0.1-1.10.2.jar` |
| Log | `run/protactinium-launch.log` |
| Run reported here | 17:36:23 to 17:36:25, 130 lines |
| Mod jar tested | 5,333,597 bytes, timestamp 17:33:52 |

Note on reproducibility: the jar was rebuilt and redeployed **several times during this session**
by another party (17:28:26, 17:29:46, 17:32:05, 17:33:52, 17:38:48). Each rebuild changed the
observed failure. The final run reported here was executed twice against the identical jar
(17:33:52) and produced the identical first error both times, so the finding is reproducible for
that jar.

## 3. The first genuine error

Exact line from the log (line 119):

```
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: Caused by:
org.spongepowered.asm.mixin.injection.throwables.InjectionError: Critical injection failure:
Callback method actinium$finishPreRenderGlErrorAndStartSoundTiming(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V
in mixins.actinium.iris.json:features.iris.MinecraftGlDebugMixin from mod actinium failed
injection check, (0/1) succeeded. Scanned 1 target(s). Using refmap mixins.actinium-refmap.json
```

The outermost exception (line 96) is only a cascade:

```
[17:36:25] ... java.lang.NoClassDefFoundError: net/minecraft/client/Minecraft
[17:36:25] ... Caused by: java.lang.ClassNotFoundException: Exception caught while transforming class net.minecraft.client.Minecraft
```

`Minecraft` is not missing. It fails to load because the transformer threw while applying
`MinecraftGlDebugMixin`. The root cause is the `InjectionError` above.

### Classification

Mixin injection apply failure (target not matched). Not a missing class, not a
NoSuchMethodError.

### Class and method involved

| Role | Detail |
|---|---|
| Mixin class | `com.dhj.actinium.mixin.features.iris.MinecraftGlDebugMixin` |
| Config | `mixins.actinium.iris.json` (declared at log line 79) |
| Callback method | `actinium$finishPreRenderGlErrorAndStartSoundTiming(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V` |
| Target class | `net.minecraft.client.Minecraft` |
| Target method | `runGameLoop` (`func_71411_J`) |
| Injection point | `@At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundHandler;setListener(Lnet/minecraft/entity/player/EntityPlayer;F)V")` |
| Source location | `src/main/java/com/dhj/actinium/mixin/features/iris/MinecraftGlDebugMixin.java:101` |
| Result | `(0/1) succeeded` - the call site was not found |

## 4. Root cause, established by inspection

The injection target is written with a **MCP name**, `setListener`. In a production client the
method is named `func_147691_a`. Resolution therefore depends entirely on the refmap translating
`setListener` to `func_147691_a`.

Four facts, each verified directly:

1. The mixin writes the target as an MCP member name:

```
at = @At(value = "INVOKE",
         target = "Lnet/minecraft/client/audio/SoundHandler;setListener(Lnet/minecraft/entity/player/EntityPlayer;F)V")
```

2. The SRG map confirms `setListener` is `func_147691_a` in the shipped client:

```
MD: net/minecraft/client/audio/SoundHandler/setListener (Lnet/minecraft/entity/player/EntityPlayer;F)V net/minecraft/client/audio/SoundHandler/func_147691_a (Lnet/minecraft/entity/player/EntityPlayer;F)V
```

3. The shipped refmap **has an entry for this mixin but no mapping for this target**. The whole
`MinecraftGlDebugMixin` entry is:

```json
{
  "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(FJ)V": "Lnet/minecraft/client/renderer/EntityRenderer;func_181560_a(FJ)V",
  "Lnet/minecraft/client/Minecraft;updateDisplay()V": "Lnet/minecraft/client/Minecraft;func_175601_h()V",
  "Lnet/minecraft/client/Minecraft;startNanoTime:J": "Lnet/minecraft/client/Minecraft;field_181543_z:J",
  "Lnet/minecraft/client/Minecraft;fpsCounter:I": "Lnet/minecraft/client/Minecraft;field_71420_M:I",
  "Lnet/minecraft/client/Minecraft;frameTimer:Lnet/minecraft/util/FrameTimer;": "Lnet/minecraft/client/Minecraft;field_181542_y:Lnet/minecraft/util/FrameTimer;",
  "Lnet/minecraft/client/shader/Framebuffer;bindFramebuffer(Z)V": "Lnet/minecraft/client/shader/Framebuffer;func_147610_a(Z)V",
  "runGameLoop": "Lnet/minecraft/client/Minecraft;func_71411_J()V",
  "runTick()V": "Lnet/minecraft/client/Minecraft;func_71407_l()V",
  "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V": "Lnet/minecraft/client/renderer/GlStateManager;func_179094_E()V",
  "Lnet/minecraft/client/shader/Framebuffer;framebufferRender(II)V": "Lnet/minecraft/client/shader/Framebuffer;func_147615_c(II)V",
  "Lnet/minecraft/client/renderer/EntityRenderer;renderStreamIndicator(F)V": "Lnet/minecraft/client/renderer/EntityRenderer;func_152430_c(F)V",
  "checkGLError(Ljava/lang/String;)V": "Lnet/minecraft/client/Minecraft;func_71361_d(Ljava/lang/String;)V",
  "Lnet/minecraft/client/Minecraft;displayDebugInfo(J)V": "Lnet/minecraft/client/Minecraft;func_71366_a(J)V",
  "Lnet/minecraft/client/Minecraft;checkGLError(Ljava/lang/String;)V": "Lnet/minecraft/client/Minecraft;func_71361_d(Ljava/lang/String;)V"
}
```

There is no `setListener` and no `func_147691_a` anywhere in the entire refmap file. Verified by
string search: the shipped `mixins.actinium-refmap.json` contains neither token.

4. The call site genuinely exists in `runGameLoop`, so the target is correct; only the name
resolution is missing. Disassembling `Minecraft.class` from `build/forgeBin-at.jar` shows
`invokevirtual ... SoundHandler.setListener:(Lnet/minecraft/entity/player/EntityPlayer;F)V`
inside `private void runGameLoop()` (bytecode offset 235).

Conclusion: the annotation processor failed to emit a mapping for this `@At` target. Mixin then
searched the obfuscated class for a literal `setListener`, found nothing, and the injection
failed.

## 5. Known history: none of the three regressed

| Historic failure | Occurred this run? | Evidence |
|---|---|---|
| `ClassCastException ... Adapter cannot be cast to Comparable` | No | `ClassCastException` count = 0 |
| `NoClassDefFoundError org/antlr/v4/runtime/CharStream` | No | no `antlr` line in log; `NoClassDefFoundError` count is 1 and that one is `net/minecraft/client/Minecraft` |
| `ClassNotFoundException: mixin ...BufferBuilderMixin` | No | no `BufferBuilder` line in log |

The earlier `@Shadow field buffer` Tessellator failure from my first two launches is also gone. The
current `MixinTessellator` declares its shadow with the SRG name directly
(`private VertexBuffer field_178183_a;`), so it no longer needs a refmap entry to resolve.

## 6. Required counts (final run, 17:36)

Taken from `run/protactinium-launch.log`, 130 lines.

| Pattern | Count |
|---|---|
| `FAILED during APPLY` | 0 |
| `InvalidMixinException` | 0 |
| `NoSuchMethodError` | 0 |
| `NoClassDefFoundError` | 1 |
| `ClassCastException` | 0 |
| `MixinApplyError` | 0 |

## 7. Mixins actually applied

**Zero mixins applied successfully.** Searches for `Mixin apply`, `Mixins:` and
`Successfully applied` return 0 hits that indicate success. The only progress lines are the four
config registrations:

```
[17:36:23] Loading early loader com.dhj.actinium.mixins.MixinEarly for its mixins.
[17:36:23] Adding [mixins.actinium.vintage.json] mixin configuration.
[17:36:23] Adding [mixins.actinium.iris.json] mixin configuration.
```

That is registration, not application. Transformation aborted on the first target.

## 8. Actinium / Protactinium / celeritas / terrain mentions

| Pattern | Count |
|---|---|
| `Actinium` or `Protactinium` | 4 (all config registration and the fatal line) |
| `celeritas`, `Celeritas`, `indirect terrain`, `terrain renderer` | 0 |

No terrain, celeritas or renderer code was ever reached.

## 9. Other failures observed during the session (superseded, not current)

The rebuilt jars produced two other first errors along the way. Both are recorded because either
could return if the corresponding code is restored.

1. **Missing refmap entirely.** Jar 17:18:15 shipped 0 refmap files while the configs declared
   `"refmap": "mixins.actinium-refmap.json"`, giving
   `@Shadow field buffer was not located in the target class ... No refMap loaded.` The build now
   embeds the refmap; that specific message no longer appears.

2. **`NoSuchMethodError` on a log4j overload.** Jar 17:32:05 threw:
   ```
   java.lang.NoSuchMethodError: 'void org.apache.logging.log4j.Logger.debug(java.lang.String, java.lang.Object)'
       at com.dhj.actinium.compat.MixinReEntranceLockFix.preloadClasses(MixinReEntranceLockFix.java:120)
   ```
   Cause: `Logger.debug(String, Object)` does not exist in log4j 2.0-beta9, which is what Forge
   1.10.2 ships. Only `debug(String, Object...)` exists. The source now concatenates into
   `LOGGER.debug("Pre-loaded " + clazz.getName())`, which resolves, and the error no longer occurs.
   Any future two-argument `LOGGER.debug(...)` with a non-String second argument will reintroduce it.

## 10. Latent second failure, expected next

Two injections in the same mixin target `net.minecraft.client.gui.toasts.GuiToast`
(`MinecraftGlDebugMixin.java:176` and `:184`). **That class does not exist in 1.10.2 at all.** The
`toasts` package is a 1.12 addition; a search of `build/forgeBin-at.jar` returns 0 entries matching
`toasts`. The refmap also contains no `GuiToast` or `drawToast` mapping.

These injections did not get the chance to fail because `setListener` failed first in the same
mixin. Once the current error is fixed, these two are the most likely next failure. They should be
removed or made conditional for a 1.10.2 target.

## 11. Last 30 lines of the log

```
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:571]: 	at System//net.minecraft.launchwrapper.Launch.launch(Launch.java:35)
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:571]: 	at System//net.minecraft.launchwrapper.Launch.main(Launch.java:60)
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:571]: 	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:571]: 	at java.base/java.lang.reflect.Method.invoke(Method.java:565)
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:571]: 	at com.gtnewhorizons.retrofuturabootstrap.Main.main(Main.java:207)
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:579]: Caused by: java.lang.ClassNotFoundException: Exception caught while transforming class net.minecraft.client.Minecraft
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:579]: 	at System//net.minecraft.launchwrapper.LaunchClassLoader.findClass(LaunchClassLoader.java:295)
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:579]: 	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:557)
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:579]: 	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:490)
[17:36:25] [main/INFO]: [java.lang.ThreadGroup:uncaughtException:579]: 	... 9 more
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:686]: Caused by: org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: An unexpected critical error was encountered
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:686]: 	at System//org.spongepowered.asm.mixin.transformer.MixinProcessor.applyMixins(MixinProcessor.java:375)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:686]: 	at System//org.spongepowered.asm.mixin.transformer.MixinTransformer.transformClass(MixinTransformer.java:238)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:686]: 	at System//org.spongepowered.asm.mixin.transformer.MixinTransformer.transformClassBytes(MixinTransformer.java:203)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:686]: 	at System//org.spongepowered.asm.mixin.transformer.Proxy.transform(Proxy.java:70)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:686]: 	at System//net.minecraft.launchwrapper.LaunchClassLoader.runTransformers(LaunchClassLoader.java:409)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:686]: 	at System//net.minecraft.launchwrapper.LaunchClassLoader.findClass(LaunchClassLoader.java:293)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:686]: 	... 11 more
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError: Critical injection failure: Callback method actinium$finishPreRenderGlErrorAndStartSoundTiming(Lo... (truncated here, full text in section 3)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.injection.struct.InjectionInfo.postInject(InjectionInfo.java:534)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.transformer.MixinTargetContext.applyInjections(MixinTargetContext.java:1513)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard.applyInjections(MixinApplicatorStandard.java:840)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard.lambda$runApplicatorPass$7(MixinApplicatorStandard.java:370)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard.processMixins(MixinApplicatorStandard.java:389)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard.runApplicatorPass(MixinApplicatorStandard.java:368)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard.apply(MixinApplicatorStandard.java:248)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.transformer.TargetClassContext.apply(TargetClassContext.java:437)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.transformer.TargetClassContext.applyMixins(TargetClassContext.java:418)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	at System//org.spongepowered.asm.mixin.transformer.MixinProcessor.applyMixins(MixinProcessor.java:347)
[17:36:25] [main/INFO]: [java.lang.Throwable:printStackTrace:709]: 	... 16 more
```

## 12. Recommendation, in priority order

1. **Fix the refmap generation for `@At` targets, not just `@Inject method` and `@Shadow`.** The
   processor is emitting entries for method names and shadow fields but is dropping `@At` INVOKE
   member descriptors. Build config already passes `-AMSG_CANT_FIND_MAPPING=warning` and
   `-AMSG_TARGET_ELEMENT_NOT_FOUND=warning` (build.ps1 lines 175 to 182), which downgrades exactly
   this class of problem to a warning that nobody reads. Raise `-AMSG_CANT_FIND_MAPPING` to an
   error, or post-process the refmap and assert that every `@At` target in every mixin has a
   mapping. `MSG_NO_OBFDATA_FOR_SHADOW=warning` deserves the same treatment.

2. **Short term, unblock the launch.** Either write the missing mappings explicitly, or annotate
   the affected injections with `remap = false` together with the SRG name, for example
   `target = "Lnet/minecraft/client/audio/SoundHandler;func_147691_a(...)V"`. The latter is the
   pattern already used successfully for `MixinTessellator.field_178183_a`.

3. **Remove the `GuiToast` injections.** `net.minecraft.client.gui.toasts` does not exist in
   1.10.2. Those two injections cannot ever match and will fail as soon as the current one is
   fixed. See section 10.

4. **Audit every `@At` target in `mixins.actinium.iris.json` against the 1.10.2 jar, not against
   1.12.2 source.** The failing target, the `GuiToast` target, and the historically wrong
   Tessellator shadow all share one cause: this mixin set was written for a later Minecraft
   version and only partially translated. Fixing failures one at a time will be slow.

5. **Freeze the jar for the next diagnostic run.** Five rebuilds landed during this session and
   each one changed the reported failure, which makes before/after comparison unreliable. Record
   the jar hash in the report and do not redeploy mid-run.

## 13. Reproduce

```
powershell -NoProfile -ExecutionPolicy Bypass -File "D:\MuRong\cwsgi12313\MRPlus\protactinium-dev\launch-test.ps1" -TimeoutSec 170
```

Then read `run/protactinium-launch.log`. The first genuine error is the `InjectionError` on
`MinecraftGlDebugMixin`, not the `NoClassDefFoundError: net/minecraft/client/Minecraft` that
appears above it in the stack output.
