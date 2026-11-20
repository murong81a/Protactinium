package com.dhj.actinium.mixin.features.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.TesrBrackets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hands every tile entity renderer call to the TESR batch brackets. */
@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcherTesr {

    @WrapOperation(
        method = "func_178469_a(Lnet/minecraft/tileentity/TileEntity;DDDFI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;func_180535_a(Lnet/minecraft/tileentity/TileEntity;DDDFI)V"
        )
    )
    private void actinium$renderTesrBatched(TileEntitySpecialRenderer<TileEntity> renderer, TileEntity te,
                                            double x, double y, double z, float partialTicks, int destroyStage,
                                            Operation<Void> original) {
        // No alpha argument: that parameter belongs to the 1.12.2 signature of renderTileEntityAt.
        // In 1.10.2 the method is renderTileEntityAt(TileEntity, double, double, double, float, int),
        // and specifying an extra parameter makes Mixin reject the wrapper with
        // "invalid signature. Found unexpected argument type float at index 7".
        TesrBrackets.renderTileEntity(renderer, te, x, y, z, partialTicks, destroyStage,
            () -> original.call(renderer, te, x, y, z, partialTicks, destroyStage));
    }
}
