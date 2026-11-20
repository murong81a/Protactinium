package com.dhj.actinium.mixin.vintage.features.render.tileentity.piston;

import net.minecraft.client.renderer.tileentity.TileEntityPistonRenderer;
import net.minecraft.tileentity.TileEntityPiston;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TileEntityPistonRenderer.class)
public class MixinTileEntityPistonRenderer {
    /**
     * @author embeddedt
     * @reason Pistons appear to flicker if the TESR stops rendering before the chunk mesh updates with the block model.
     * To avoid this, always render the TESR even after progress reaches 100%.
     */
    // renderTileEntityAt is the 1.10.2 name; 1.12.2 renamed it render. func_180535_a is its SRG form.
    @Redirect(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityPiston;DDDFI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntityPiston;func_145860_a(F)F", ordinal = 0))
    private float alwaysRenderTESR(TileEntityPiston instance, float ticks) {
        return 0f;
    }
}
