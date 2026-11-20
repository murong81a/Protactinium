package com.dhj.actinium.mixin.features.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.VanillaModelMeshes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelLargeChest;
import net.minecraft.client.renderer.tileentity.TileEntityChestRenderer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Draws chests from a cached model instead of re-tessellating them every frame.
 *
 * <p>Every member name here is the SRG name, because a production 1.10.2 client carries those:
 * {@code func_180535_a} for the render method, {@code func_78231_a} for the model draw and
 * {@code func_147499_a} for the texture bind. The deobfuscated jar this project compiles against
 * shows the MCP spellings instead, so the names cannot be read from it.</p>
 */
@Mixin(TileEntityChestRenderer.class)
public abstract class MixinTileEntityChestRenderer {

    /**
     * Records the sheet the renderer binds.
     *
     * <p>{@code index = 0} selects the first argument of the matched call. The method binds several
     * textures; the first is the chest sheet, which is the one the cached draw has to rebind.</p>
     */
    // Standard injectors such as @ModifyArg have their selector remapped by the annotation processor,
    // so the MCP name is required here. Only the MixinExtras injectors take an SRG name.
    @ModifyArg(
        method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityChest;DDDFI)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/renderer/tileentity/TileEntityChestRenderer;func_147499_a(Lnet/minecraft/util/ResourceLocation;)V"),
        index = 0
    )
    private ResourceLocation actinium$captureTexture(ResourceLocation texture,
                                                     @Share("texture") LocalRef<ResourceLocation> boundTexture) {
        boundTexture.set(texture);
        return texture;
    }

    @WrapOperation(
        method = "func_180535_a(Lnet/minecraft/tileentity/TileEntityChest;DDDFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelChest;func_78231_a()V")
    )
    private void actinium$renderCachedChest(ModelChest model, Operation<Void> original,
                                            @Local(argsOnly = true) TileEntityChest chest,
                                            @Local(argsOnly = true) int destroyStage,
                                            @Share("texture") LocalRef<ResourceLocation> boundTexture) {
        if (destroyStage < 0 && chest.hasWorld() && VanillaModelMeshes.canRenderCached()) {
            VanillaModelMeshes.renderChest(model, boundTexture.get(), model instanceof ModelLargeChest);
        } else {
            original.call(model);
        }
    }
}
