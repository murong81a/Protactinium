package com.dhj.actinium.mixin.features.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.VanillaModelMeshes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.tileentity.TileEntityEnderChestRenderer;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Draws ender chests from a cached model.
 *
 * <p>SRG names throughout, for the same reason as the chest mixin: a production client carries
 * {@code func_180535_a}, {@code func_78231_a} and {@code func_147499_a}.</p>
 */
@Mixin(TileEntityEnderChestRenderer.class)
public abstract class MixinTileEntityEnderChestRenderer {

    // Standard injectors such as @ModifyArg have their selector remapped by the annotation processor,
    // so the MCP name is required here. Only the MixinExtras injectors take an SRG name.
    @ModifyArg(
        method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityEnderChest;DDDFI)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/renderer/tileentity/TileEntityEnderChestRenderer;func_147499_a(Lnet/minecraft/util/ResourceLocation;)V"),
        index = 0
    )
    private ResourceLocation actinium$captureTexture(ResourceLocation texture,
                                                     @Share("texture") LocalRef<ResourceLocation> boundTexture) {
        boundTexture.set(texture);
        return texture;
    }

    @WrapOperation(
        method = "func_180535_a(Lnet/minecraft/tileentity/TileEntityEnderChest;DDDFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelChest;func_78231_a()V")
    )
    private void actinium$renderCachedChest(ModelChest model, Operation<Void> original,
                                            @Local(argsOnly = true) TileEntityEnderChest chest,
                                            @Local(argsOnly = true) int destroyStage,
                                            @Share("texture") LocalRef<ResourceLocation> boundTexture) {
        if (destroyStage < 0 && chest.hasWorld() && VanillaModelMeshes.canRenderCached()) {
            VanillaModelMeshes.renderChest(model, boundTexture.get(), false);
        } else {
            original.call(model);
        }
    }
}
