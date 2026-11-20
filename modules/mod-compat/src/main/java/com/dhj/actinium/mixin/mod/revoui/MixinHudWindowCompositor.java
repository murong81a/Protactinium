package com.dhj.actinium.mixin.mod.revoui;

import com.dhj.actinium.render.GuiGlStateBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "neofontrender.addons.hud.compositor.HudWindowCompositor", remap = false)
public abstract class MixinHudWindowCompositor {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void actinium$restoreCompositorEntryState(float partialTicks, CallbackInfo ci) {
        GuiGlStateBoundary.restoreHudBaseline();
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void actinium$restoreCompositorExitState(float partialTicks, CallbackInfo ci) {
        GuiGlStateBoundary.restoreHudBaseline();
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lneofontrender/addons/hud/compositor/HudSurface;render(F)V"
        ),
        remap = false
    )
    // Isolate the state Revo surfaces mutate while leaving compositor-owned scissor state untouched.
    private void actinium$renderSurfaceWithStateBoundary(Object surface, float partialTicks) {
        GuiGlStateBoundary.CompositorSurfaceState previous = GuiGlStateBoundary.beginCompositorSurface();
        try {
            renderSurface(surface, partialTicks);
        } finally {
            previous.restore();
            GuiGlStateBoundary.restoreHudBaseline();
        }
    }

    /**
     * Forwards to the surface's own render method.
     *
     * <p>Revo's HUD surface type belongs to a mod with no 1.10.2 build, so the call is made through
     * a {@code MethodHandle} bound on first use. The enclosing mixin targets the compositor by
     * name, so it is skipped entirely when the mod is absent.</p>
     */
    private static void renderSurface(Object surface, float partialTicks) {
        try {
            java.lang.invoke.MethodHandle handle = RENDER_SURFACE;
            if (handle == null) {
                Class<?> surfaceClass = Class.forName("neofontrender.addons.hud.compositor.HudSurface");
                handle = java.lang.invoke.MethodHandles.lookup().findVirtual(surfaceClass, "render",
                        java.lang.invoke.MethodType.methodType(void.class, float.class));
                RENDER_SURFACE = handle;
            }
            handle.invoke(surface, partialTicks);
        } catch (Throwable t) {
            com.dhj.actinium.runtime.ActiniumRuntime.logger().error(
                    "Revo HUD surface render call failed", t);
        }
    }

    private static volatile java.lang.invoke.MethodHandle RENDER_SURFACE;
}