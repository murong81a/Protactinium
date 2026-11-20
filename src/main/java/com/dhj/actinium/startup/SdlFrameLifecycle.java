package com.dhj.actinium.startup;

/**
 * Per-frame hooks for the SDL backend, kept as no-ops.
 *
 * <p>Upstream calls these at the start and end of every client frame so the SDL backend can pump
 * its own event loop and present the Vulkan swapchain. That backend is out of scope for this
 * port, and it is the only caller of these methods, so they do nothing.</p>
 *
 * <p>The methods exist rather than the call sites being deleted so the client frame mirrors
 * upstream exactly; a reader comparing the two files sees the same ordering, and if the SDL
 * backend is ever ported the calls are already in place.</p>
 */
public final class SdlFrameLifecycle {

    private SdlFrameLifecycle() {}

    /** Called at the start of a client frame. No-op without the SDL backend. */
    public static void beginFrame() {
        // No-op by design.
    }

    /** Called at the end of a client frame. No-op without the SDL backend. */
    public static void endFrame() {
        // No-op by design.
    }
}
