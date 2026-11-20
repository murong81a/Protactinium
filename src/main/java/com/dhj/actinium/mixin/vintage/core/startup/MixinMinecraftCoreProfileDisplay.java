package com.dhj.actinium.mixin.vintage.core.startup;

import com.dhj.actinium.startup.CoreProfileDisplayCreator;
import net.minecraft.client.Minecraft;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.PixelFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps Minecraft's display startup flow intact while replacing its context creation call. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftCoreProfileDisplay {
    /**
     * Creates Actinium's core-profile context without bypassing other mixins on {@code createDisplay}.
     *
     * @param format pixel format requested by Minecraft
     * @throws LWJGLException if no supported core-profile context can be created
     */
    @Redirect(
        method = "createDisplay",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/Display;create(Lorg/lwjgl/opengl/PixelFormat;)V",
            remap = false
        )
    )
    private void celeritas$createCoreProfileDisplay(PixelFormat format) throws LWJGLException {
        // The parameter type is org.lwjgl's, which is what Mixin checks against: the redirect is
        // validated against the target bytecode before lwjgl3ify rewrites the call site, so declaring
        // the compatibility layer's type makes Mixin reject the handler with
        // "Found unexpected argument type org.lwjglx.opengl.PixelFormat at index 0". At run time the
        // value handed over is the layer's object, which is why CoreProfileDisplayCreator carries an
        // overload for it; the compiler binds this call to the org.lwjgl overload, and the layer's
        // PixelFormat is what actually arrives, so the overload pair is what makes both stages agree.
        CoreProfileDisplayCreator.create(format.withStencilBits(8));
    }
}
