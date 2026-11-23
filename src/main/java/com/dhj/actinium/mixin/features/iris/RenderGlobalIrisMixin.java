package com.dhj.actinium.mixin.features.iris;

import com.dhj.actinium.render.CloudRenderSettings;
import com.dhj.actinium.render.iris.EntityOutlinePass;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import com.gtnewhorizons.angelica.glsm.CompatUniformManager;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class RenderGlobalIrisMixin {
    @Shadow(remap = false) @Final
    private RenderManager field_175010_j;

    @Unique
    private final EntityOutlinePass actinium$entityOutlinePass = new EntityOutlinePass();

    @WrapMethod(method = "func_180446_a")
    private void actinium$scopeEntityOutlinePass(Entity viewer, ICamera camera, float partialTicks, Operation<Void> original) {
        this.actinium$entityOutlinePass.withinEntityRender(() -> original.call(viewer, camera, partialTicks));
    }

    @Inject(method = "func_180446_a", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/shader/Framebuffer;func_147614_f()V"))
    private void actinium$beginEntityOutlinePass(Entity viewer, ICamera camera, float partialTicks, CallbackInfo ci) {
        if (actinium$getPipeline() instanceof DeferredWorldRenderingPipeline pipeline && pipeline.shouldOverrideShaders()) {
            this.actinium$entityOutlinePass.begin(pipeline.getRenderTargetStateListener(), () -> {
                this.field_175010_j.setRenderOutlines(false);
                pipeline.restoreActivePass();
            });
        }
    }

    @Inject(method = "func_180446_a",
        slice = @Slice(from = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;entityOutlineShader:Lnet/minecraft/client/shader/ShaderGroup;")),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;func_147610_a(Z)V",
            ordinal = 0, shift = At.Shift.AFTER))
    private void actinium$endEntityOutlinePass(Entity viewer, ICamera camera, float partialTicks, CallbackInfo ci) {
        this.actinium$entityOutlinePass.finish();
    }

    @Unique
    private final org.joml.Matrix4f actinium$skyModelView = new org.joml.Matrix4f();

    @Unique
    private boolean actinium$skyModelViewSaved;

    @Inject(method = "renderSky(FI)V", at = @At("HEAD"))
    private void actinium$beginSky(float partialTicks, int pass, CallbackInfo ci) {
        // The sky rotates the model view by the celestial angle and must leave the stack the way it
        // found it. It does not on this version: the leftover rotation then tilts every later draw
        // that shares the stack, which is what moved entities off the ground while the terrain, with
        // its own matrices, stayed correct. Keep the camera model view and put it back afterwards.
        actinium$skyModelView.set(com.gtnewhorizons.angelica.glsm.GLSMMatrices.getModelViewMatrix());
        actinium$skyModelViewSaved = true;

        if (!Iris.enabled) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.CUSTOM_SKY);
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At("RETURN"))
    private void actinium$endSky(float partialTicks, int pass, CallbackInfo ci) {
        if (actinium$skyModelViewSaved) {
            actinium$skyModelViewSaved = false;
            com.gtnewhorizons.angelica.glsm.GLSMMatrices.setModelViewMatrix(actinium$skyModelView);
        }

        if (!Iris.enabled) {
            return;
        }

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }

    @Redirect(
        method = "renderClouds",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/GameSettings;func_181147_e()I")
    )
    private int actinium$useConfiguredCloudMode(GameSettings settings) {
        return CloudRenderSettings.effectiveCloudMode(settings.clouds);
    }

    @Inject(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;func_179090_x()V",
            ordinal = 0
        )
    )
    private void actinium$beginVanillaSky(float partialTicks, int pass, CallbackInfo ci) {
        actinium$setSkyPhase(WorldRenderingPhase.SKY);
    }

    @Inject(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;func_76560_a(FF)[F")
    )
    private void actinium$beginSunset(float partialTicks, int pass, CallbackInfo ci) {
        actinium$setSkyPhase(WorldRenderingPhase.SUNSET);
    }

    @Inject(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;func_179098_w()V",
            ordinal = 0
        )
    )
    private void actinium$endSunset(float partialTicks, int pass, CallbackInfo ci) {
        actinium$setSkyPhase(WorldRenderingPhase.SKY);
    }

    // Measured against 1.10.2's own bytecode rather than assumed:
    //   * both calls live in renderSky(float, int), whose SRG name is func_174976_a;
    //   * the invocation owner is WorldClient, because that is the static type at the call site,
    //     even though getCelestialAngle is declared on World.
    // The ordinal selects the second call, which is the one made where the sun may be rotated.
    @Inject(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;func_72826_c(F)F",
            ordinal = 0
        )
    )
    private void actinium$applySunPathRotation(float partialTicks, int pass, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = actinium$getPipeline();
        if (pipeline != null) {
            GlStateManager.rotate(pipeline.getSunPathRotation(), 0.0F, 0.0F, 1.0F);
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;func_110577_a(Lnet/minecraft/util/ResourceLocation;)V",
            ordinal = 0
        )
    )
    private void actinium$bindSun(TextureManager textureManager, ResourceLocation location) {
        actinium$setSkyPhase(WorldRenderingPhase.SUN);
        textureManager.bindTexture(location);
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;func_110577_a(Lnet/minecraft/util/ResourceLocation;)V",
            ordinal = 1
        )
    )
    private void actinium$bindMoon(TextureManager textureManager, ResourceLocation location) {
        actinium$setSkyPhase(WorldRenderingPhase.MOON);
        textureManager.bindTexture(location);
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78381_a()V", ordinal = 1)
    )
    private void actinium$drawSun(Tessellator tessellator) {
        WorldRenderingPipeline pipeline = actinium$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderSun();
        if (directive) {
            actinium$refreshCurrentProgramCompatUniforms(pipeline);
            tessellator.draw();
        } else {
            actinium$discard(tessellator.getBuffer());
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78381_a()V", ordinal = 2)
    )
    private void actinium$drawMoon(Tessellator tessellator) {
        WorldRenderingPipeline pipeline = actinium$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderMoon();
        if (directive) {
            actinium$refreshCurrentProgramCompatUniforms(pipeline);
            tessellator.draw();
        } else {
            actinium$discard(tessellator.getBuffer());
        }
        actinium$setSkyPhase(WorldRenderingPhase.STARS);
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;func_177358_a(I)V", ordinal = 0)
    )
    private void actinium$drawSkyDiscVbo(net.minecraft.client.renderer.vertex.VertexBuffer vertexBuffer, int mode) {
        WorldRenderingPipeline pipeline = actinium$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderSkyDisc();
        if (directive) {
            vertexBuffer.drawArrays(mode);
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;func_179148_o(I)V", ordinal = 0)
    )
    private void actinium$drawSkyDiscList(int list) {
        WorldRenderingPipeline pipeline = actinium$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderSkyDisc();
        if (directive) {
            GlStateManager.callList(list);
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;func_177358_a(I)V", ordinal = 1)
    )
    private void actinium$drawStarsVbo(net.minecraft.client.renderer.vertex.VertexBuffer vertexBuffer, int mode) {
        actinium$setSkyPhase(WorldRenderingPhase.STARS);
        WorldRenderingPipeline pipeline = actinium$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderStars();
        if (directive) {
            vertexBuffer.drawArrays(mode);
        }
    }

    @Redirect(
        method = "renderSky(FI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;func_179148_o(I)V", ordinal = 1)
    )
    private void actinium$drawStarsList(int list) {
        actinium$setSkyPhase(WorldRenderingPhase.STARS);
        WorldRenderingPipeline pipeline = actinium$getPipeline();
        boolean directive = pipeline == null || pipeline.shouldRenderStars();
        if (directive) {
            GlStateManager.callList(list);
        }
    }

    private static WorldRenderingPipeline actinium$getPipeline() {
        return Iris.enabled ? Iris.getPipelineManager().getPipelineNullable() : null;
    }

    private static void actinium$setSkyPhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = actinium$getPipeline();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }

    private static void actinium$refreshCurrentProgramCompatUniforms(WorldRenderingPipeline pipeline) {
        if (pipeline instanceof DeferredWorldRenderingPipeline deferredPipeline) {
            int program = deferredPipeline.getActivePassProgramId();
            if (program > 0) {
                CompatUniformManager.onUseProgram(program);
            }
        }
    }

    private static void actinium$discard(VertexBuffer bufferBuilder) {
        try {
            bufferBuilder.finishDrawing();
        } catch (IllegalStateException ignored) {
        }
        bufferBuilder.reset();
    }
}
