package com.dhj.actinium.mixin.features.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.VanillaModelMeshes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelSign;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TileEntitySignRenderer.class)
public abstract class MixinTileEntitySignRenderer {

    @Shadow(remap = false) @Final private static ResourceLocation field_147513_b;

    @WrapOperation(
        method = "func_180535_a(Lnet/minecraft/tileentity/TileEntitySign;DDDFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelSign;func_78164_a()V")
    )
    private void actinium$renderCachedBoard(ModelSign model, Operation<Void> original, @Local(argsOnly = true) int destroyStage) {
        if (destroyStage < 0 && VanillaModelMeshes.canRenderCached()) {
            VanillaModelMeshes.renderSign(model, field_147513_b);
        } else {
            original.call(model);
        }
    }
}
