package com.dhj.actinium.mixin.features.iris;

import com.dhj.actinium.render.CloudRenderSettings;
import com.dhj.actinium.render.GuiGlStateBoundary;
import com.dhj.actinium.render.RenderWorldRecursionGuard;
import com.dhj.actinium.render.RevoScreenEffectsGradient;
import com.dhj.actinium.render.terrain.ActiniumWorldRenderer;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dhj.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import net.coderbot.iris.Iris;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.debug.IrisGlDebug;
import net.coderbot.iris.gl.framebuffer.MinecraftFramebufferHelper;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.pipeline.SkyRenderDistance;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.CloudSetting;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.tesr.EntityShadowReplay;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererIrisMixin implements IResourceManagerReloadListener {
    @Unique
    private boolean actinium$wireframeActive;

    @Shadow(remap = false)
    public Minecraft field_78531_r;

    // 1.10.2 declares func_180437_a(RenderGlobal, float, int); the x/y/z parameters were added
    // in a later version, so the shadow carries the 1.10.2 shape.
    @Shadow(remap = false)
    private void func_180437_a(RenderGlobal renderGlobalIn, float partialTicks, int pass) {
    }

    @Shadow(remap = false)
    protected abstract void func_78474_d(float partialTicks);

    @Shadow(remap = false)
    private void func_78484_h() {
    }

    // A truly nested renderWorldPass would observe the swapped Minecraft.world and flip the active
    // pipeline mid-pass; the guard lets the Iris injections below skip nested passes. BPR-style
    // sequential portal views are served by PipelineManager's per-dimension cache, not this guard.
    @WrapMethod(method = "func_175068_a(IFJ)V")
    private void actinium$guardRenderWorldPassRecursion(int pass, float partialTicks, long finishTimeNano, Operation<Void> original) {
        RenderWorldRecursionGuard.enter();
        try {
            original.call(pass, partialTicks, finishTimeNano);
        } finally {
            RenderWorldRecursionGuard.exit();
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("HEAD"))
    private void actinium$beginWorldPassTiming(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        IrisGlDebug.beginWorldPassTiming(pass);
        if (Iris.shouldActivateWireframe() && this.field_78531_r.isSingleplayer()) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            this.actinium$wireframeActive = true;
        }
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At("RETURN"))
    private void actinium$finishWorldPassTiming(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (this.actinium$wireframeActive) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            this.actinium$wireframeActive = false;
        }
        IrisGlDebug.finishWorldPassTiming();
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;getInstance()Lnet/minecraft/client/renderer/culling/ClippingHelper;", shift = At.Shift.AFTER)
    )
    private void actinium$beginIrisWorld(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled) {
            return;
        }

        DHCompat.checkFrame();
        Iris.tryLoadShaderpackWhenPossible();
        if (!IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            return;
        }

        CapturedRenderingState.INSTANCE.setTickDelta(partialTicks);
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());

        Program.unbind();

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimensionName());
        BlockRenderingSettings.INSTANCE.reloadRendererIfRequired();
        GLSMCurrentAttribs.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        pipeline.beginLevelRendering();
        IrisGlDebug.markStage("mixin:begin-world:done");
        pipeline.renderPreSkyPrepare();
        IrisGlDebug.markStage("mixin:pre-sky-prepare:done");
        IrisGlDebug.recordWorldPassStage("iris-begin-to-sky");

        // The camera model view is the one the whole world pass must use. Later stages (sky, weather,
        // clouds, shadows) run on the same OpenGL matrix stack; a stage that does not restore it
        // leaves every entity drawn against the wrong camera. Keep the camera here and put it back
        // before the entity loop.
        actinium$cameraSnapshot.set(com.gtnewhorizons.angelica.glsm.GLSMMatrices.getModelViewMatrix());
        actinium$cameraSnapshotValid = true;
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174970_a(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V", shift = At.Shift.AFTER)
    )
    private void actinium$renderIrisShadows(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci,
                                            @Local ICamera playerCamera) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled || !IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) {
            return;
        }

        // The shadow pass only holds the shadow frustum, so hand it the player viewport the terrain pass just used.
        if (playerCamera instanceof ViewportProvider viewportProvider) {
            ActiniumWorldRenderer renderer = ActiniumWorldRenderer.instanceNullable();
            if (renderer != null) {
                renderer.setShadowPassPlayerViewport(viewportProvider.sodium$createViewport());
            }
        }

        actinium$prepareRenderManagerForShadowPass(partialTicks);
        IrisGlDebug.markStage("mixin:shadows:entry");
        pipeline.renderShadows((EntityRenderer) (Object) this, Camera.INSTANCE);
        IrisGlDebug.markStage("mixin:shadows:done");
        IrisGlDebug.recordWorldPassStage("setup-terrain-to-shadows");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;func_180446_a(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            shift = At.Shift.BEFORE
        )
    )
    private void actinium$publishMainViewFrustum(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci,
                                                  @Local ICamera camera) {
        // The shadow replay only returns draws for entities the main view still covers, so the main
        // frustum has to reach the replay bookkeeping before the entity loop starts.
        if (AngelicaConfig.entityShadowReplayEnabled()) {
            EntityShadowReplay.setMainView(camera);
        }
        if (actinium$cameraSnapshotValid) {
            com.gtnewhorizons.angelica.glsm.GLSMMatrices.setModelViewMatrix(actinium$cameraSnapshot);
        }
    }

    @Unique
    private static final org.joml.Matrix4f actinium$cameraSnapshot = new org.joml.Matrix4f();

    @Unique
    private static boolean actinium$cameraSnapshotValid;

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;func_74583_a(Lnet/minecraft/entity/player/EntityPlayer;Z)V", shift = At.Shift.AFTER)
    )
    private void actinium$captureCameraState(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        EntityLivingBase viewEntity = (EntityLivingBase) this.field_78531_r.getRenderViewEntity();
        if (viewEntity == null) {
            return;
        }

        Camera.INSTANCE.update(viewEntity, partialTicks);
        RenderingState.INSTANCE.setCameraPosition(
            Camera.INSTANCE.getPos().x,
            Camera.INSTANCE.getPos().y,
            Camera.INSTANCE.getPos().z
        );
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderEndNanoTime:J",
            opcode = Opcodes.PUTFIELD,
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    // Somnia rewrites the renderWorld call in updateCameraAndRender to SomniaUtil.renderWorld,
    // so anchor this stage to the first field write that follows the world render instead.
    private void actinium$checkAfterRenderWorld(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDebug.markStage("entity-renderer:after-render-world");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174975_c()V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterEntityOutlineFramebuffer(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDebug.markStage("entity-renderer:after-entity-outline-fbo");
    }

    // 1.10.2 drives the entity shader group through loadShaderGroup(float); render(float) only exists
    // from 1.11 onward, so the stage marker anchors to loadShaderGroup.
    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/ShaderGroup;func_148018_a(F)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterShaderGroup(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDebug.markStage("entity-renderer:after-shader-group");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;func_175180_a(F)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterGameOverlay(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDebug.markStage("entity-renderer:after-game-overlay");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;func_175180_a(F)V")
    )
    private void actinium$restoreBeforeGameOverlay(float partialTicks, long nanoTime, CallbackInfo ci) {
        // renderItemActivation runs after the first GUI setup and may change state before the HUD starts.
        GuiGlStateBoundary.restoreHudBaseline();
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78478_c()V")
    )
    private void actinium$restoreBeforeGui(float partialTicks, long nanoTime, CallbackInfo ci) {
        MinecraftFramebufferHelper.restoreMainFramebuffer(true);
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78478_c()V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void actinium$restoreAfterFirstSetupOverlay(float partialTicks, long nanoTime, CallbackInfo ci) {
        GuiGlStateBoundary.restoreHudBaseline();
        RevoScreenEffectsGradient.drawIfPending(this.field_78531_r);
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V", shift = At.Shift.AFTER, remap = false)
    )
    private void actinium$checkAfterDrawScreen(float partialTicks, long nanoTime, CallbackInfo ci) {
        IrisGlDebug.markStage("entity-renderer:after-draw-screen");
    }

    @Inject(
        method = "updateCameraAndRender(FJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V", remap = false)
    )
    private void actinium$restoreGuiScreenState(float partialTicks, long nanoTime, CallbackInfo ci) {
        GuiGlStateBoundary.restoreHudBaseline();
    }

    private void actinium$prepareRenderManagerForShadowPass(float partialTicks) {
        RenderManager renderManager = this.field_78531_r.getRenderManager();
        renderManager.cacheActiveRenderInfo(
            this.field_78531_r.world,
            this.field_78531_r.fontRendererObj,
            this.field_78531_r.getRenderViewEntity(),
            this.field_78531_r.pointedEntity,
            this.field_78531_r.gameSettings,
            partialTicks
        );
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;func_179086_m(I)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterClear(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("clear-to-camera");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78479_a(FI)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterCameraTransform(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("camera-to-active-render-info");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;func_74583_a(Lnet/minecraft/entity/player/EntityPlayer;Z)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterActiveRenderInfo(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("active-render-info-to-frustum");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;getInstance()Lnet/minecraft/client/renderer/culling/ClippingHelper;", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterClippingHelper(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("clipping-helper-to-culling");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ICamera;func_78547_a(DDD)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterFrustumPosition(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("culling-to-sky-fog");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78468_a(IF)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterSkyFog(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-fog-to-projection");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174976_a(FI)V")
    )
    private void actinium$checkBeforeSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("render-sky-call");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;func_179128_n(I)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterSkyProjectionMatrixMode(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-matrix-mode-to-load-identity");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;func_179096_D()V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterSkyProjectionLoadIdentity(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-load-identity-to-fov");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78481_a(FZ)F", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterSkyFov(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-fov-to-perspective");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterSkyPerspective(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.recordWorldPassStage("sky-perspective-to-modelview");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174976_a(FI)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-sky");
        IrisGlDebug.recordWorldPassStage("render-sky-to-clouds");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_180437_a(Lnet/minecraft/client/renderer/RenderGlobal;FI)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterClouds(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (Iris.enabled && IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            IrisGlDebug.recordWorldPassStage("cloud-check-to-setup-terrain");
            return;
        }

        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-clouds");
        IrisGlDebug.recordWorldPassStage("clouds-to-setup-terrain");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174970_a(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterSetupTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-setup-terrain");
        IrisGlDebug.recordWorldPassStage("setup-terrain-to-update-chunks");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174967_a(J)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterUpdateChunks(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-update-chunks");
        IrisGlDebug.recordWorldPassStage("update-chunks-to-terrain-solid");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 0)
    )
    private void actinium$checkAfterSolidTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-terrain-solid");
        IrisGlDebug.recordWorldPassStage("terrain-solid-to-cutout-mipped");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 1)
    )
    private void actinium$checkAfterCutoutMippedTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-terrain-cutout-mipped");
        IrisGlDebug.recordWorldPassStage("terrain-cutout-mipped-to-cutout");
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
            ordinal = 2
        )
    )
    private int actinium$skipDuplicateConsolidatedCutout(
        RenderGlobal renderGlobal,
        BlockRenderLayer blockLayer,
        double partialTicks,
        int pass,
        Entity entity
    ) {
        if (blockLayer == BlockRenderLayer.CUTOUT
            && ActiniumWorldRenderer.instance().getRenderPassConfiguration()
                .usesSameRenderPass(BlockRenderLayer.CUTOUT_MIPPED, BlockRenderLayer.CUTOUT)) {
            return 0;
        }

        return renderGlobal.renderBlockLayer(blockLayer, partialTicks, pass, entity);
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 2)
    )
    private void actinium$checkAfterCutoutTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-terrain-cutout");
        IrisGlDebug.recordWorldPassStage("terrain-cutout-to-entities-0");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_180446_a(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", shift = At.Shift.AFTER, ordinal = 0)
    )
    private void actinium$checkAfterEntitiesPass0(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.check("render-world-pass:" + pass + ":after-entities-0");
        IrisGlDebug.recordWorldPassStage("entities-0-to-selection-box");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_72731_b(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/math/RayTraceResult;IF)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterSelectionBox(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-selection-box");
        IrisGlDebug.recordWorldPassStage("selection-box-to-block-damage");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174981_a(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/VertexBuffer;Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-block-damage");
        IrisGlDebug.recordWorldPassStage("block-damage-to-lit-particles");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;func_78872_b(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterLitParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-lit-particles");
        IrisGlDebug.recordWorldPassStage("lit-particles-to-particles");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;func_78874_a(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-particles");
        IrisGlDebug.recordWorldPassStage("particles-to-weather");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78474_d(F)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-weather");
        IrisGlDebug.recordWorldPassStage("weather-to-terrain-translucent");
    }

    // GTCEu's GregTechTransformer rewrites renderWorldPass via ASM and replaces the 4th
    // renderBlockLayer call (TRANSLUCENT) with BloomEffectUtil.renderBloomBlockLayer, so this
    // ordinal no longer exists under GregTech. Debug-stage marker only; tolerate its absence.
    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I", shift = At.Shift.AFTER, ordinal = 3),
        require = 0
    )
    private void actinium$checkAfterTranslucentTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-terrain-translucent");
        IrisGlDebug.recordWorldPassStage("terrain-translucent-to-entities-1");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_180446_a(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", shift = At.Shift.AFTER, ordinal = 1)
    )
    private void actinium$checkAfterEntitiesPass1(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.check("render-world-pass:" + pass + ":after-entities-1");
        IrisGlDebug.recordWorldPassStage("entities-1-to-render-last");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", shift = At.Shift.AFTER, remap = false)
    )
    private void actinium$checkAfterRenderLast(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-render-last");
        IrisGlDebug.recordWorldPassStage("render-last-to-hand");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78476_b(FI)V", shift = At.Shift.AFTER)
    )
    private void actinium$checkAfterHand(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        IrisGlDebug.check("render-world-pass:" + pass + ":after-hand");
        IrisGlDebug.recordWorldPassStage("hand-to-return");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", remap = false)
    )
    private void actinium$finalizeIrisWorld(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled || !IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            actinium$renderLateClouds(partialTicks, pass, pipeline);
        }

        MinecraftFramebufferHelper.restoreMinecraftFramebufferBuffers();
        IrisGlDebug.markStage("mixin:finalize:entry");
        if (pipeline == null) {
            return;
        }

        IrisGlDebug.markStage("mixin:finalize:before-hand-translucent");
        HandRenderer.INSTANCE.renderTranslucent(partialTicks, Camera.INSTANCE, this.field_78531_r.renderGlobal, pipeline);
        IrisGlDebug.markStage("mixin:finalize:after-hand-translucent");
        this.field_78531_r.mcProfiler.endStartSection("iris_final");
        pipeline.finalizeLevelRendering();
        IrisGlDebug.markStage("mixin:finalize:after-pipeline");
        Program.unbind();
        GLSMDepthStencilAlpha.glDepthMask(true);
        IrisGlDebug.markStage("mixin:finalize:done");
        IrisGlDebug.recordWorldPassStage("finalize-to-render-last");
    }

    private void actinium$renderLateClouds(float partialTicks, int pass, WorldRenderingPipeline pipeline) {
        if (pipeline.getCloudSetting() == CloudSetting.OFF) {
            return;
        }

        pipeline.setPhase(WorldRenderingPhase.CLOUDS);
        try {
            this.func_180437_a(this.field_78531_r.renderGlobal, partialTicks, pass);
        } finally {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
        IrisGlDebug.markStage("render-world-pass:" + pass + ":after-clouds");
        IrisGlDebug.recordWorldPassStage("clouds-to-finalize");
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174976_a(FI)V")
    )
    private void actinium$beginSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.CUSTOM_SKY);
        }
    }

    @Inject(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174976_a(FI)V", shift = At.Shift.AFTER)
    )
    private void actinium$endSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (RenderWorldRecursionGuard.isNested()) {
            return;
        }
        if (!Iris.enabled) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    // CubicChunks redirects the same renderDistanceChunks reads for its vertical view distance.
    // ModifyExpressionValue composes with that @Redirect instead of fighting it: when Actinium
    // applies first, CubicChunks' value is still clamped by effectiveChunks; when CubicChunks
    // applies first, no field read remains to wrap and this handler stays inactive (require = 0).
    @ModifyExpressionValue(
        method = "func_175068_a(IFJ)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"),
        require = 0
    )
    private int actinium$alwaysRenderSky(int original) {
        return SkyRenderDistance.effectiveChunks(original);
    }

    // See actinium$alwaysRenderSky for the CubicChunks coexistence contract.
    @ModifyExpressionValue(
        method = "func_78479_a(FI)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"),
        require = 0
    )
    private int actinium$alwaysUseSkyRenderDistanceForProjection(int original) {
        return SkyRenderDistance.effectiveChunks(original);
    }

    // See actinium$alwaysRenderSky for the CubicChunks coexistence contract.
    @ModifyExpressionValue(
        method = "func_78466_h(F)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I"),
        require = 0
    )
    private int actinium$alwaysApplySunsetColors(int original) {
        return SkyRenderDistance.effectiveChunks(original);
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_180437_a(Lnet/minecraft/client/renderer/RenderGlobal;FI)V")
    )
    private void actinium$renderClouds(EntityRenderer renderer, RenderGlobal renderGlobal, float partialTicks, int pass) {
        if (RenderWorldRecursionGuard.isNested()) {
            this.func_180437_a(renderGlobal, partialTicks, pass);
            return;
        }
        if (!Iris.enabled || !IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            this.func_180437_a(renderGlobal, partialTicks, pass);
        }
    }

    @Redirect(
        method = "func_180437_a",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/GameSettings;func_181147_e()I")
    )
    private int actinium$useConfiguredCloudMode(GameSettings settings) {
        return CloudRenderSettings.effectiveCloudMode(settings.clouds);
    }

    @Inject(
        method = "func_180437_a",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78468_a(IF)V", shift = At.Shift.AFTER)
    )
    private void actinium$disableFogForClouds(CallbackInfo ci) {
        if (this.field_78531_r.gameSettings.renderDistanceChunks < SkyRenderDistance.MINIMUM_RENDER_DISTANCE_CHUNKS) {
            GLStateManager.disableFog();
        }
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78474_d(F)V")
    )
    private void actinium$renderWeather(EntityRenderer renderer, float partialTicks) {
        if (RenderWorldRecursionGuard.isNested()) {
            this.func_78474_d(partialTicks);
            return;
        }
        if (!Iris.enabled) {
            this.func_78474_d(partialTicks);
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) {
            this.func_78474_d(partialTicks);
            return;
        }

        pipeline.setPhase(WorldRenderingPhase.RAIN_SNOW);
        if (pipeline.shouldWriteRainAndSnowToDepthBuffer()) {
            GLSMDepthStencilAlpha.glDepthMask(true);
        }
        if (pipeline.shouldRenderWeather()) {
            this.func_78474_d(partialTicks);
        }
        pipeline.setPhase(WorldRenderingPhase.NONE);
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_180449_a(Lnet/minecraft/entity/Entity;F)V")
    )
    private void actinium$renderWorldBorder(RenderGlobal renderGlobal, net.minecraft.entity.Entity entity, float partialTicks) {
        if (RenderWorldRecursionGuard.isNested()) {
            renderGlobal.renderWorldBorder(entity, partialTicks);
            return;
        }
        if (!Iris.enabled) {
            renderGlobal.renderWorldBorder(entity, partialTicks);
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.WORLD_BORDER);
        }
        renderGlobal.renderWorldBorder(entity, partialTicks);
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;func_190073_a(FJ)V")
    )
    private void actinium$renderDebug(DebugRenderer debugRenderer, float partialTicks, long finishTimeNano) {
        if (RenderWorldRecursionGuard.isNested()) {
            debugRenderer.renderDebug(partialTicks, finishTimeNano);
            return;
        }
        if (!Iris.enabled) {
            debugRenderer.renderDebug(partialTicks, finishTimeNano);
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.DEBUG);
        }
        debugRenderer.renderDebug(partialTicks, finishTimeNano);
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Redirect(
        method = "updateRenderer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78484_h()V")
    )
    private void actinium$renderRainParticles(EntityRenderer renderer) {
        if (!Iris.enabled) {
            this.func_78484_h();
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null || pipeline.shouldRenderWeatherParticles()) {
            this.func_78484_h();
        }
    }

    @Redirect(
        method = "renderWorldPass(IFJ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174981_a(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/VertexBuffer;Lnet/minecraft/entity/Entity;F)V")
    )
    private void actinium$renderBlockDamage(RenderGlobal renderGlobal, Tessellator tessellator, net.minecraft.client.renderer.VertexBuffer bufferBuilder, net.minecraft.entity.Entity entity, float partialTicks) {
        if (RenderWorldRecursionGuard.isNested()) {
            renderGlobal.drawBlockDamageTexture(tessellator, bufferBuilder, entity, partialTicks);
            return;
        }
        if (!Iris.enabled) {
            renderGlobal.drawBlockDamageTexture(tessellator, bufferBuilder, entity, partialTicks);
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.DESTROY);
        }
        renderGlobal.drawBlockDamageTexture(tessellator, bufferBuilder, entity, partialTicks);
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Redirect(
        method = "renderHand(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;func_78440_a(F)V")
    )
    private void actinium$disableVanillaShaderHand(ItemRenderer itemRenderer, float partialTicks) {
        boolean shaderPackInUse = IrisApi.getInstance().isShaderPackInUse();
        if (!shaderPackInUse) {
            itemRenderer.renderItemInFirstPerson(partialTicks);
        }
    }
}
