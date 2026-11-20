package com.dhj.actinium.mixin.vintage.core.vertex;

import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.dhj.actinium.render.VanillaVertexBufferRenderer;

import java.nio.ByteBuffer;

@Mixin(VertexBuffer.class)
public class MixinVertexBuffer {
    @Shadow(remap = false)
    private int field_177365_a;

    @Shadow(remap = false)
    @Final
    private VertexFormat field_177363_b;

    @Shadow(remap = false)
    private int field_177364_c;

    @Inject(method = "bufferData", at = @At("HEAD"), cancellable = true)
    private void celeritas$coreProfileBufferData(ByteBuffer data, CallbackInfo ci) {
        VanillaVertexBufferRenderer.uploadVertexBuffer(this.field_177363_b, this.field_177365_a, data, GL15.GL_STATIC_DRAW);
        this.field_177364_c = data.limit() / this.field_177363_b.getNextOffset();
        ci.cancel();
    }

    @Inject(method = "drawArrays", at = @At("HEAD"), cancellable = true)
    private void celeritas$coreProfileDrawArrays(int mode, CallbackInfo ci) {
        VanillaVertexBufferRenderer.drawVertexBuffer(this.field_177363_b, this.field_177365_a, this.field_177364_c, mode);
        ci.cancel();
    }

    @Inject(method = "deleteGlBuffers", at = @At("HEAD"), cancellable = true)
    private void celeritas$coreProfileDeleteGlBuffers(CallbackInfo ci) {
        if (this.field_177365_a >= 0) {
            VanillaVertexBufferRenderer.deleteVertexBuffer(this.field_177365_a);
            this.field_177365_a = -1;
        }
        ci.cancel();
    }
}

