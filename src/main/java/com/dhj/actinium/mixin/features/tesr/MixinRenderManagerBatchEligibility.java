package com.dhj.actinium.mixin.features.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.TesrBrackets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderManager.class)
public abstract class MixinRenderManagerBatchEligibility {
    @Shadow(remap = false)
    private boolean field_178639_r;

    @WrapOperation(
        method = "func_188391_a",
        // require = 0: this is a batching optimisation, not a correctness fix. Mixin reports the
        // callback as unmatched on this target even though the call is present in the bytecode, and
        // a required match that fails aborts the whole mixin, which takes the render manager down
        // with it. Skipping the optimisation is the correct trade here; see REMAINING_WORK.md.
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/Render;func_76986_a(Lnet/minecraft/entity/Entity;DDDFF)V"
        )
    )
    private void actinium$observeDoRender(Render<Entity> render, Entity entity, double x, double y, double z,
                                          float yaw, float partialTicks, Operation<Void> original) {
        if (this.field_178639_r) {
            original.call(render, entity, x, y, z, yaw, partialTicks);
            return;
        }
        TesrBrackets.renderEntity(render, entity, partialTicks,
            () -> original.call(render, entity, x, y, z, yaw, partialTicks));
    }
}
