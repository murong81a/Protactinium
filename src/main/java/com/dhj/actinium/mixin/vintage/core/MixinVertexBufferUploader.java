package com.dhj.actinium.mixin.vintage.core;

import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.VertexBufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBufferUploader.class)
public class MixinVertexBufferUploader {
    @Shadow(remap = false)
    private net.minecraft.client.renderer.vertex.VertexBuffer field_178179_a;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void celeritas$coreProfileDraw(VertexBuffer bufferBuilder, CallbackInfo ci) {
        this.field_178179_a.bufferData(bufferBuilder.getByteBuffer());
        bufferBuilder.reset();
        ci.cancel();
    }
}
