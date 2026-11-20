package com.dhj.actinium.mixin.vintage.core.crash;

import org.lwjglx.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"net/minecraftforge/fml/client/SplashProgress$1"})
public class MixinSplashProgressCallable {
    @Inject(method = "call", at = @At("HEAD"), cancellable = true, remap = false)
    private void checkContext(CallbackInfoReturnable<String> cir) {
        boolean isContextAvailable;
        try {
            isContextAvailable = Display.isCreated() && Display.getDrawable().isCurrent();
        } catch (Exception e) {
            isContextAvailable = false;
        }
        if (!isContextAvailable) {
            cir.setReturnValue("No context available");
        }
    }
}
