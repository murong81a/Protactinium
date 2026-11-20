package com.dhj.actinium.mixin.features.iris;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import net.minecraft.tileentity.TileEntityBeacon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityBeaconRenderer.class)
public class TileEntityBeaconRendererIrisMixin {
    @Inject(
        method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFI)V",
        at = @At("HEAD")
    )
    private void actinium$beginBeaconBeam(
        TileEntityBeacon tileEntity,
        double x,
        double y,
        double z,
        float partialTicks,
        int destroyStage,
        CallbackInfo ci
    ) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.BEACON_BEAM);
    }

    @Inject(
        method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityBeacon;DDDFI)V",
        at = @At("RETURN")
    )
    private void actinium$endBeaconBeam(
        TileEntityBeacon tileEntity,
        double x,
        double y,
        double z,
        float partialTicks,
        int destroyStage,
        CallbackInfo ci
    ) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
