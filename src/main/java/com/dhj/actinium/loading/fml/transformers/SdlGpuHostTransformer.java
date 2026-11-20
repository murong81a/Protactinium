package com.dhj.actinium.loading.fml.transformers;

import java.util.List;

/**
 * Class transformer that would route {@code Display} and {@code Drawable} calls into the SDL
 * backend, reduced to a pass-through.
 *
 * <p>The real transformer rewrites {@code org.lwjglx.opengl.Display} and {@code DrawableGL} so that
 * display creation, {@code makeCurrent} and the end-frame path can be handed to the SDL window and
 * a Vulkan drawable. That backend is out of scope for this port, so there is nothing to route
 * into: every method returns the input bytes unchanged.</p>
 *
 * <p>This class is still registered with the transformer list and still reports its targets,
 * because the registration order and target list are part of the loading contract that the rest
 * of the early-loading code follows. Turning the transformer into a no-op rather than removing the
 * registration keeps that contract intact and keeps the file comparable with upstream.</p>
 */
public final class SdlGpuHostTransformer implements ExplicitClassTransformer {

    /** Classes the upstream transformer would rewrite. */
    public static final List<String> TARGETS = List.of(
            "org.lwjglx.opengl.Display",
            "org.lwjglx.opengl.DrawableGL");

    @Override
    public byte[] transform(byte[] basicClass) {
        return basicClass;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        return basicClass;
    }
}
