package com.dhj.actinium.mixin.vintage.core;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobalDestroyBlock {
    @Unique
    private int celeritas$blockDamageMinFilter = GL11.GL_NEAREST_MIPMAP_LINEAR;

    @Unique
    private int celeritas$blockDamageMagFilter = GL11.GL_NEAREST;

    @Inject(
            method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/VertexBuffer;Lnet/minecraft/entity/Entity;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_180443_s()V")
    )
    private void celeritas$beginBlockDamageNearest(Tessellator tessellator, VertexBuffer bufferBuilder, Entity entity, float partialTicks, CallbackInfo ci) {
        TextureInfo info = TextureInfoCache.INSTANCE.getInfo(GLStateManager.getBoundTextureForServerState());
        this.celeritas$blockDamageMinFilter = info.getMinFilter();
        this.celeritas$blockDamageMagFilter = info.getMagFilter();
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    @Inject(
            method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/VertexBuffer;Lnet/minecraft/entity/Entity;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174969_t()V")
    )
    private void celeritas$endBlockDamageNearest(Tessellator tessellator, VertexBuffer bufferBuilder, Entity entity, float partialTicks, CallbackInfo ci) {
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, this.celeritas$blockDamageMinFilter);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, this.celeritas$blockDamageMagFilter);
    }
}
