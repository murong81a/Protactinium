package com.gtnewhorizons.angelica.glsm.backend;

/**
 * Selects the window toolkit used by the renderer.
 *
 * <p>This port carries GLFW only. Upstream Actinium also has an SDL adapter, but that adapter
 * belongs to the sdl-gpu subtree (an alternate windowing and GPU backend built on SDL 3 plus
 * Vulkan), which is out of scope here: the target instance is a Forge 1.10.2 client whose
 * window belongs to Minecraft itself, so only the GLFW path that reads that window is used.
 * The class is kept rather than deleted so call sites stay identical to upstream.</p>
 */
public final class WindowBackends {
    private static WindowBackend backend;

    private WindowBackends() {}

    /** Returns the selected adapter, initializing GLFW by default. */
    public static synchronized WindowBackend get() {
        if (backend == null) {
            backend = GlfwHolder.INSTANCE;
        }
        return backend;
    }

    /**
     * Kept for source compatibility with upstream. The SDL adapter is not part of this port, so
     * this logs the request and leaves the GLFW adapter in place.
     */
    public static synchronized void useSdl() {
        org.apache.logging.log4j.LogManager.getLogger("GLSM")
                .warn("SDL window backend is not part of this port; continuing with GLFW");
    }

    /** Selects GLFW, retaining the same adapter on repeated calls. */
    public static synchronized void useGlfw() {
        if (!(backend instanceof GlfwWindowBackend)) {
            backend = GlfwHolder.INSTANCE;
        }
    }

    private static final class GlfwHolder {
        private static final WindowBackend INSTANCE = new GlfwWindowBackend();
    }
}
