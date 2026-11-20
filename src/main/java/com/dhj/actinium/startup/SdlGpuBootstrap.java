package com.dhj.actinium.startup;

import java.io.File;

import org.lwjglx.LWJGLException;
import org.lwjglx.opengl.Display;
import org.lwjglx.opengl.Drawable;
import org.lwjglx.opengl.PixelFormat;

/**
 * SDL / Vulkan render backend bootstrap, reduced to its disabled form.
 *
 * <p>Upstream Actinium can hand rendering to an SDL 3 window plus a Vulkan GPU backend
 * (`sdl-gpu`), selected at startup and reached through Outlands' backend-selection API. That
 * backend is out of scope for this port: the target is a Forge 1.10.2 client whose window and GL
 * context belong to Minecraft, and the SDL/Vulkan libraries have no 1.10.2 packaging here.</p>
 *
 * <p>This class keeps the public surface so every call site stays identical to upstream, and
 * answers "not supported" everywhere. That is the same code path upstream takes when the backend
 * is unsupported, which is what every client without Outlands and the SDL libraries already
 * does, so the behaviour that remains is the GLFW/OpenGL path this port actually ships.</p>
 *
 * <p>Deleted rather than stubbed would have meant editing nine call sites across the startup,
 * display-binding, GUI and font-rendering code to remove feature checks that are already correct
 * as written; keeping the checks and answering false leaves those files comparable with upstream
 * and ready for the backend if it is ever ported.</p>
 */
public final class SdlGpuBootstrap {

    private SdlGpuBootstrap() {}

    /** Whether the backend could run here at all. */
    public static boolean isSupported() {
        return false;
    }

    /** Startup hook. Nothing to initialise, because the backend cannot be selected. */
    public static synchronized void initialize() {
        // Intentionally empty: no SDL context is created and no backend is registered.
    }

    /** Whether the SDL backend is the active renderer. Always false in this port. */
    public static boolean isActive() {
        return false;
    }

    /** Ensures the SDL drawable is installed. No-op without the backend. */
    public static void ensureDrawableInstalled() {
        // No-op by design.
    }

    /** Client-loading-complete hook. */
    public static void onLoadingComplete() {
        // No-op by design.
    }

    /**
     * Creates the display through the SDL backend.
     *
     * @return false, meaning "not handled, use the normal path"
     */
    public static boolean createDisplay(Object format) {
        return false;
    }

    /**
     * Whether a drawable belongs to the SDL backend.
     *
     * @return false, so the caller treats every drawable as an LWJGL one
     */
    public static boolean isSharedDrawable(Object drawable) {
        return false;
    }

    /**
     * Makes a drawable current through the SDL backend.
     *
     * <p>Declared with the same signature as upstream so the surrounding code compiles
     * unchanged. It is unreachable while {@link #isActive()} is false, and it reports that
     * clearly rather than silently succeeding.</p>
     */
    public static void makeCurrent(Drawable drawable) throws LWJGLException {
        throw new LWJGLException("SDL backend is not part of this port");
    }

    /** Directory the backend would keep its guard file in. Kept for call-site compatibility. */
    public static File configDirectory() {
        return new File("config/actinium");
    }
}
