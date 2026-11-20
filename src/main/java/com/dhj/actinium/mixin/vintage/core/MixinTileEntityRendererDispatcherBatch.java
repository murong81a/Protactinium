package com.dhj.actinium.mixin.vintage.core;

import com.dhj.actinium.render.terrain.TileEntityBatchDrawGuard;
import com.dhj.actinium.runtime.ActiniumRuntime;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import dhj.embeddedt.embeddium.api.shader.buffer.VertexBufferExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcherBatch {
    @Unique
    private static boolean actinium$warnedFinishedBatch;

    // drawBatch is not in the SRG, which means 1.10.2 does not obfuscate it; the production client
    // keeps this name, so remapping is disabled for it.
    @Redirect(
        method = "drawBatch",
        remap = false,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;func_78381_a()V")
    )
    private void actinium$drawBatchIfBuilding(Tessellator tessellator) {
        VertexBuffer buffer = tessellator.getBuffer();
        boolean building = ((VertexBufferExtension) buffer).actinium$isDrawing();
        if (!TileEntityBatchDrawGuard.drawIfBuilding(building, tessellator::draw)
            && !actinium$warnedFinishedBatch) {
            actinium$warnedFinishedBatch = true;
            ActiniumRuntime.logger().warn(
                "Skipping an already-finished Forge tile entity batch after a nested renderer flushed it"
            );
        }
    }
}
