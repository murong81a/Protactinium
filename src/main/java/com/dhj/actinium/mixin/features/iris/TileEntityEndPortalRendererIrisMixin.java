package com.dhj.actinium.mixin.features.iris;

import com.dhj.actinium.render.EndPortalRenderPolicy;
import com.dhj.actinium.render.EndPortalRenderer;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.debug.IrisGlDebug;
import net.minecraft.client.renderer.tileentity.TileEntityEndPortalRenderer;
import net.minecraft.tileentity.TileEntityEndPortal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes end portal rendering through the core-profile renderer when a shader pack is active.
 *
 * <p>The 1.12.2 source reads two helper values off the vanilla renderer, {@code getPasses(distance)}
 * and {@code getOffset()}. Neither method exists in 1.10.2: that renderer is hard-coded to sixteen
 * layers at a fixed offset, which its own bytecode shows as {@code bipush 16} and the constant
 * {@code 0.75f}. The values are therefore written here as constants matching that bytecode rather
 * than shadowed, which is both simpler and correct for this version. A {@code @Shadow} for a method
 * that does not exist fails the whole mixin, and the failure takes the renderer class down with it,
 * which is why this was a hard crash rather than a visual glitch.</p>
 */
@Mixin(TileEntityEndPortalRenderer.class)
public abstract class TileEntityEndPortalRendererIrisMixin {

    /** Layer count the 1.10.2 renderer uses; its bytecode passes 16 to the layer loop. */
    @Unique
    private static final int ACTINIUM_PORTAL_LAYERS = 16;

    /** Depth offset the 1.10.2 renderer uses; its bytecode loads the constant 0.75f. */
    @Unique
    private static final float ACTINIUM_PORTAL_OFFSET = 0.75F;

    @Unique
    private static int actinium$portalLogCount;

    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityEndPortal;DDDFI)V",
            at = @At("HEAD"), cancellable = true)
    private void actinium$renderGtnhPortal(
            TileEntityEndPortal te,
            double x,
            double y,
            double z,
            float partialTicks,
            int destroyStage,
            CallbackInfo ci
    ) {
        if (!EndPortalRenderPolicy.shouldUseReplacementRenderer(te, IrisApiV0Impl.INSTANCE.isShaderPackInUse())) {
            // A synthetic render call without a world, such as BetterPortals' starfield overlay, with
            // no shader pack active: keep the vanilla path so its blend-state hooks still apply
            // through the fixed-function simulation.
            return;
        }
        ci.cancel();

        if (IrisGlDebug.shouldLogPortalRenderEvents() && actinium$portalLogCount++ < 8) {
            IrisGlDebug.logDebugInfo("end-portal-core-profile type={} pos=[{},{},{}]",
                    te.getClass().getName(), x, y, z);
        }

        EndPortalRenderer.render(te, x, y, z, ACTINIUM_PORTAL_OFFSET, ACTINIUM_PORTAL_LAYERS);
    }
}
