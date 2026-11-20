package com.dhj.actinium.mixin.mod.cofhcore;

import com.dhj.actinium.render.GuiGlStateBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the translucent GUI state after CoFH's configuration tab draws.
 *
 * <p>The tab disables blending after drawing its own panel icons, while the
 * parent continues drawing the remaining tabs in the same foreground pass.</p>
 */
// CoFH Core has no 1.10.2 build, so the target is named as a string. Mixin resolves it at
// apply time and skips the mixin when the class is absent, which is the behaviour wanted.
@Mixin(targets = "cofh.core.gui.element.tab.TabConfigurationTransfer", remap = false)
public abstract class MixinTabConfigurationTransfer {
    @Inject(method = "drawForeground", at = @At("RETURN"), remap = false)
    private void actinium$restoreGuiTabState(CallbackInfo ci) {
        GuiGlStateBoundary.beginTranslucentLayer();
    }
}
