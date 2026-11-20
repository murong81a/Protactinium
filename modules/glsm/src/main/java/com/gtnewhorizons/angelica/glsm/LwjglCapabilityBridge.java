package com.gtnewhorizons.angelica.glsm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjglx.opengl.ContextCapabilities;

import java.lang.reflect.Field;

/**
 * Fills the compatibility layer's {@link ContextCapabilities} from the real LWJGL 3 view.
 *
 * <p>lwjgl3ify's {@code org.lwjglx.opengl.GLContext} in this build constructs an empty
 * {@code ContextCapabilities} and never writes a single field, so every capability query routed
 * through the compat layer answers false. That disabled features the hardware actually has, and
 * the first visible consequences were</p>
 *
 * <ul>
 *   <li>shader packs with compute stages dying at load with
 *       {@code IllegalStateException: This PC does not support compute shaders};</li>
 *   <li>{@code COMPUTE_SHADERS / SSBO / CUSTOM_IMAGES / PER_BUFFER_BLENDING} all reported
 *       unusable, and GPU chunk culling falling back to CPU for the same reason.</li>
 * </ul>
 *
 * <p><b>The real capabilities are read through reflection, deliberately.</b> A compile-time
 * reference to {@code org.lwjgl.opengl.GL} in this jar is rewritten by lwjgl3ify's redirector to
 * {@code org.lwjglx.opengl.GL}, a class that does not exist in this trimmed port, and the first
 * version of this bridge died exactly there at startup. Reflection by class name bypasses the
 * rewrite, and {@code org.lwjgl.*} is excluded from the launch class loader, so the lookup
 * resolves against the parent class loader where the real LWJGL 3 lives.</p>
 *
 * <p>Called once from {@link GLStateManager#preInit} while a context is current. Fields absent
 * from the real view stay false, which is the compat layer's own default and a safe answer for
 * anything untested.</p>
 */
public final class LwjglCapabilityBridge {

    private static final Logger LOGGER = LogManager.getLogger("GTNHLib");

    private LwjglCapabilityBridge() {}

    public static void populate() {
        // The compat object GLStateManager.capabilities holds is the single shared instance the
        // stub's GLContext.getCapabilities() hands out, so fetching it here fills the same object
        // every compat query reads. org.lwjglx references are never rewritten by the redirector.
        final ContextCapabilities compat = org.lwjglx.opengl.GLContext.getCapabilities();
        if (compat == null) {
            return;
        }

        final Object realCaps;
        try {
            final Class<?> gl = Class.forName("org.lwjgl.opengl.GL");
            realCaps = gl.getMethod("getCapabilities").invoke(null);
        } catch (IllegalStateException noContext) {
            // No current context: every field stays false, exactly as the empty stub would have it.
            return;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            LOGGER.warn("Real LWJGL 3 capabilities are unavailable, the compat view stays empty", unavailable);
            return;
        }
        if (realCaps == null) {
            return;
        }

        int copied = 0;
        for (final Field destination : ContextCapabilities.class.getFields()) {
            if (destination.getType() != boolean.class) {
                continue;
            }
            final Field source;
            try {
                source = realCaps.getClass().getField(destination.getName());
            } catch (NoSuchFieldException absent) {
                // Not reported by this driver view: leave the default (false) alone.
                continue;
            }
            if (source.getType() != boolean.class) {
                continue;
            }
            try {
                destination.setBoolean(compat, source.getBoolean(realCaps));
                copied++;
            } catch (IllegalAccessException sealed) {
                LOGGER.warn("Could not copy capability field {}", destination.getName(), sealed);
            }
        }
        LOGGER.info("Bridged {} capability field(s) from the real LWJGL 3 view", copied);
    }
}
