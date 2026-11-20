package com.dhj.actinium.startup;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjgl.opengl.GL;

/**
 * Installs the LWJGL 3 capabilities for the context this mod created.
 *
 * <p>This class exists only to hold an {@code org.lwjgl.opengl.GL} reference that survives
 * lwjgl3ify's rewriting. That transformer maps {@code org.lwjgl.opengl.*} onto the
 * {@code org.lwjglx} compatibility layer, which has no {@code GL} class at all, so a reference to
 * {@code GL.createCapabilities()} from an ordinary class fails to link with
 * {@code NoClassDefFoundError: org/lwjglx/opengl/GL}. Marking the class {@link Lwjgl3Aware} exempts
 * it from those rewrites, which is the per-class form of that exemption; the jar-level manifest
 * attribute is deliberately not used, because it would also disable the rewrites the rest of this
 * mod depends on.</p>
 *
 * <p>The port needs this because the display is created by {@code CoreProfileDisplayCreator} rather
 * than by vanilla on LWJGL 2, and on LWJGL 3 the entry points are function pointers that exist only
 * after the capabilities are installed. Without it the first GLSM query dereferences a null pointer
 * inside the driver.</p>
 */
@Lwjgl3Aware
final class Lwjgl3Capabilities {

    private Lwjgl3Capabilities() {}

    /** Installs the capabilities when the current context does not already have them. */
    static void install() {
        if (!GL.getCapabilities().OpenGL11) {
            GL.createCapabilities();
        }
    }

    /** Reports whether a context with at least OpenGL 1.1 is current on this thread. */
    static boolean isContextCurrent() {
        return GL.getCapabilities().OpenGL11;
    }
}
