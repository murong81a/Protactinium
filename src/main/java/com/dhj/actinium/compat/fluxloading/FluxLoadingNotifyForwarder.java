package com.dhj.actinium.compat.fluxloading;

import com.dhj.actinium.runtime.ActiniumRuntime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Sole holder of FluxLoading references for the compat. Only reached behind the
 * {@code FluxLoadingCompat.IS_LOADED} guard, so FluxLoading is never resolved on an install
 * without the mod.
 *
 * <p>FluxLoading has no 1.10.2 build, so both the manager class and its chunk-source enum are
 * reached reflectively. The enum constant looked up is the one that means "a Celeritas-class
 * renderer is in use", which is the same signal the mod's own mixin sends; the difference is only
 * that the value is resolved by name instead of at compile time.</p>
 */
final class FluxLoadingNotifyForwarder {

    private static final String MANAGER_CLASS = "com.tttsaurus.fluxloading.core.FluxLoadingManager";
    private static final String SOURCE_CLASS = "com.tttsaurus.fluxloading.core.chunk.gate.FluxLoadingChunkSource";
    private static final String SOURCE_CONSTANT = "CELERITAS";

    private FluxLoadingNotifyForwarder() {
    }

    /**
     * Replays the per-frame signal of FluxLoading's own renderer mixin. The {@code CELERITAS} source
     * is the instant-complete variant: FluxLoading never waits for chunk counts behind a
     * Celeritas-class renderer, it just proceeds to the fade-out sequence.
     */
    static void notifyChunkCompileTaskProcessed() {
        try {
            Class<?> manager = Class.forName(MANAGER_CLASS);
            Class<?> source = Class.forName(SOURCE_CLASS);
            Object constant = Enum.valueOf(source.asSubclass(Enum.class), SOURCE_CONSTANT);
            MethodHandle handle = ON_CHUNK_COMPILE;
            if (handle == null) {
                handle = MethodHandles.lookup().findStatic(manager, "onChunkCompileTaskProcessed",
                        MethodType.methodType(void.class, source));
                ON_CHUNK_COMPILE = handle;
            }
            handle.invoke(constant);
        } catch (ClassNotFoundException e) {
            ActiniumRuntime.logger().debug("FluxLoading classes not found; no loading screen signal to replay", e);
        } catch (Throwable t) {
            ActiniumRuntime.logger().warn("FluxLoading chunk signal could not be forwarded", t);
        }
    }

    private static volatile MethodHandle ON_CHUNK_COMPILE;
}
