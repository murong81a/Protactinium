# Remaining work

State at hand-off: all 7 modules compile with 0 errors; the jar builds, packages and passes the
manifest, mixin-coverage and zip-integrity checks. The client reaches mixin application and stops
on the next member reference that was written against 1.12.2.

Two deterministic tools define the whole remaining task. Neither needs a launch.

## 1. Injection targets whose descriptor does not match the 1.10.2 jar (5)

```
checked 129 @At targets against forgeBin-at.jar
5 targets do not match:
  MixinTileEntityChestRenderer.java: net/minecraft/client/renderer/tileentity/TileEntityChestRenderer.bindTexture(Lnet/minecraft/util/ResourceLocation;)V
      target has: no such method
  MixinTileEntityEnderChestRenderer.java: net/minecraft/client/renderer/tileentity/TileEntityEnderChestRenderer.bindTexture(Lnet/minecraft/util/ResourceLocation;)V
      target has: no such method
  MixinTileEntityRendererDispatcherTesr.java: net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer.render(Lnet/minecraft/tileentity/TileEntity;DDDFI)V
      target has: no such method
  MixinTileEntitySkullRenderer.java: net/minecraft/client/renderer/tileentity/TileEntitySkullRenderer.bindTexture(Lnet/minecraft/util/ResourceLocation;)V
      target has: no such method
  MixinClientChunkManager.java: net/minecraft/world/chunk/Chunk.onUnload()V
      target has: no such method
```

## 2. Remapping mixins with no refmap entry (39)

A mixin that remaps needs a refmap entry for every member reference; these have none, so they fail
at apply time. The script's class-declaration regex produces a few false positives (paths like
`features/iris/only`); read each file before editing.

```
39 remapping mixins have no refmap entry
  MixinWorldProviderClouds.java  (com/dhj/actinium/mixin/features/clouds/MixinWorldProviderClouds)
  ItemIrisMixin.java  (com/dhj/actinium/mixin/features/iris/ItemIrisMixin)
  LocaleIrisMixin.java  (com/dhj/actinium/mixin/features/iris/only)
  ParticleManagerCullingMixin.java  (com/dhj/actinium/mixin/features/iris/sits)
  RenderEntityItemIrisMixin.java  (com/dhj/actinium/mixin/features/iris/RenderEntityItemIrisMixin)
  RenderGlobalSelectionOutlineMixin.java  (com/dhj/actinium/mixin/features/iris/RenderGlobalSelectionOutlineMixin)
  RenderLivingBaseDeferredOverlayMixin.java  (com/dhj/actinium/mixin/features/iris/RenderLivingBaseDeferredOverlayMixin)
  RenderManagerIrisMixin.java  (com/dhj/actinium/mixin/features/iris/transform)
  MixinLayerArmorBaseGlint.java  (com/dhj/actinium/mixin/features/tesr/MixinLayerArmorBaseGlint)
  MixinLayerHeldBlockBatchEligibility.java  (com/dhj/actinium/mixin/features/tesr/MixinLayerHeldBlockBatchEligibility)
  MixinRenderBatchEligibility.java  (com/dhj/actinium/mixin/features/tesr/MixinRenderBatchEligibility)
  MixinRenderEntityFireBatch.java  (com/dhj/actinium/mixin/features/tesr/MixinRenderEntityFireBatch)
  MixinRenderItemHeldInstancing.java  (com/dhj/actinium/mixin/features/tesr/MixinRenderItemHeldInstancing)
  MixinRenderManagerBatchEligibility.java  (com/dhj/actinium/mixin/features/tesr/MixinRenderManagerBatchEligibility)
  MixinShadowRendererEntities.java  (com/dhj/actinium/mixin/features/tesr/MixinShadowRendererEntities)
  MixinShadowRendererTesr.java  (com/dhj/actinium/mixin/features/tesr/MixinShadowRendererTesr)
  MixinTileEntityRendererDispatcherTesr.java  (com/dhj/actinium/mixin/features/tesr/MixinTileEntityRendererDispatcherTesr)
  MixinTileEntitySignRenderer.java  (com/dhj/actinium/mixin/features/tesr/MixinTileEntitySignRenderer)
  MixinTileEntitySpecialRendererBatchEligibility.java  (com/dhj/actinium/mixin/features/tesr/MixinTileEntitySpecialRendererBatchEligibility)
  TextureAtlasSpriteIrisMixin.java  (com/dhj/actinium/mixin/features/iris/startup/TextureAtlasSpriteIrisMixin)
  MixinDirection.java  (com/dhj/actinium/mixin/vintage/core/MixinDirection)
  MixinGuiScreenFpsReducerInput.java  (com/dhj/actinium/mixin/vintage/fpsreducer/MixinGuiScreenFpsReducerInput)
  MixinGuiMainMenu.java  (com/dhj/actinium/mixin/vintage/gui/MixinGuiMainMenu)
  MixinEntityRendererGuiSupersampling.java  (com/dhj/actinium/mixin/vintage/guisupersampling/MixinEntityRendererGuiSupersampling)
  MixinRenderItemGuiSupersampling.java  (com/dhj/actinium/mixin/vintage/guisupersampling/MixinRenderItemGuiSupersampling)
  MixinTileEntityItemStackRendererGuiSupersampling.java  (com/dhj/actinium/mixin/vintage/guisupersampling/MixinTileEntityItemStackRendererGuiSupersampling)
  MixinClassInheritanceMultiMap.java  (com/dhj/actinium/mixin/vintage/core/collections/MixinClassInheritanceMultiMap)
  MixinSplashProgressCallable.java  (com/dhj/actinium/mixin/vintage/core/crash/MixinSplashProgressCallable)
  MixinFrustum.java  (com/dhj/actinium/mixin/vintage/core/frustum/MixinFrustum)
  AccessorBlockColors.java  (com/dhj/actinium/mixin/vintage/core/terrain/AccessorBlockColors)
  MixinBakedQuad.java  (com/dhj/actinium/mixin/vintage/core/terrain/MixinBakedQuad)
  MixinBakedQuadFactory.java  (com/dhj/actinium/mixin/vintage/core/terrain/MixinBakedQuadFactory)
  MixinBiomeColorHelper.java  (com/dhj/actinium/mixin/vintage/core/terrain/MixinBiomeColorHelper)
  MixinBlockRendererDispatcher.java  (com/dhj/actinium/mixin/vintage/core/terrain/MixinBlockRendererDispatcher)
  MixinWorldClient.java  (com/dhj/actinium/mixin/vintage/core/terrain/MixinWorldClient)
  MixinWorldSkyColor.java  (com/dhj/actinium/mixin/vintage/core/terrain/MixinWorldSkyColor)
  MixinTextureUtil.java  (com/dhj/actinium/mixin/vintage/features/mipmaps/MixinTextureUtil)
  MixinRenderItem.java  (com/dhj/actinium/mixin/vintage/features/textures/MixinRenderItem)
  MixinTextureAtlasSprite.java  (com/dhj/actinium/mixin/vintage/features/textures/MixinTextureAtlasSprite)
```

## Method for each item

1. Read the mixin's `@Mixin` target and every `@Inject` / `@Redirect` / `@At` / `@Shadow`.
2. Get the real signature:
   `& 'D:\JAVA\Java17\bin\javap.exe' -p -classpath build\forgeBin-at.jar <class>`
3. Correct the name and descriptor. When the member genuinely has no 1.10.2 equivalent, delete that
   injection cleanly with a comment saying so (this is what `MinecraftGlDebugMixin` now does for
   the toast GUI, which arrived in 1.11).
4. Rebuild with `build.ps1` and re-run both tools until both report zero, then `cycle.ps1` to
   launch.

## Already fixed, for reference

- `Tessellator.buffer` -> `field_178183_a` (declared with its obfuscated name; the processor
  emits no entry for it)
- `SoundHandler.setListener` -> takes `EntityPlayer`
- `ActiveRenderInfo.updateRenderInfo` -> takes `EntityPlayer`
- `EntityRenderer.renderCloudsCheck` -> `(RenderGlobal, float, int)`
- TESR hooks -> `renderTileEntityAt` with descriptor `DDDFI`, not `DDDF`+`F`+`I`
- toast GUI injections -> removed, package does not exist before 1.11
- `Logger.debug(String, Object)` -> compile against the game's log4j 2.0-beta9
- 87 `@Shadow` fields verified present on their targets

## Rules that must not be relaxed

- Do not package `mod-api-stubs`; its classes are presence-probe bait (`LESSONS.md` 7).
- Do not compile against library versions newer than the game's (`LESSONS.md` 8).
- Do not declare `remap = false` on a Minecraft member; it disables the mapping that member needs.
- Do not treat "cannot find symbol" on a Lombok accessor as real until the parse phase is clean
  (`LESSONS.md` 1).
- Run `cycle.ps1` rather than a bare launch: three failures this session came from testing a
  stale jar.