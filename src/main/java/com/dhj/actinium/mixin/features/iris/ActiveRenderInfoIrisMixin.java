package com.dhj.actinium.mixin.features.iris;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.minecraft.client.renderer.ActiveRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

@Mixin(ActiveRenderInfo.class)
public class ActiveRenderInfoIrisMixin {
    @Shadow(remap = false)
    private static FloatBuffer field_178812_b;

    @Shadow(remap = false)
    private static FloatBuffer field_178813_c;

    @Inject(method = "updateRenderInfo(Lnet/minecraft/entity/player/EntityPlayer;Z)V", at = @At("TAIL"))
    private static void actinium$captureRenderMatrices(CallbackInfo ci) {
        RenderingState.INSTANCE.setProjectionMatrix(field_178813_c);
        RenderingState.INSTANCE.setModelViewMatrix(field_178812_b);
    }
}
