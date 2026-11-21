package com.dhj.actinium.startup;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger LOGGER = LogManager.getLogger("Celeritas");

    private Lwjgl3Capabilities() {}

    /**
     * Installs the capabilities for the context that is current on this thread.
     *
     * <p>The previous version tested {@code GL.getCapabilities().OpenGL11} first, which is exactly
     * the call that fails: LWJGL 3.3 throws {@code IllegalStateException: No GLCapabilities instance
     * set for the current thread} until capabilities have been created, and 0.5.x still returned a
     * defaults object instead. The guard therefore threw every time, the exception was caught and
     * reported, and no context ever got capabilities installed. Every capability GLSM and the culling
     * backend read afterwards came from a defaults object where the OpenGL 4.x version flags are
     * false, so compute, SSBO, image load/store and per-buffer blending were all reported unsupported
     * on a GL 4.6 context. Creating the capabilities re-queries the current context and is safe to
     * repeat, so it is now unconditional.
     */
    static void install() {
        GL.createCapabilities();
        refreshCompatibilityCapabilities();
    }

    /**
     * Copies the real capability flags into the compatibility layer's capability object.
     *
     * <p>LWJGL 3 reports this context as 4.6 with compute, but GLSM reads the flags from
     * {@code org.lwjglx.opengl.ContextCapabilities}, whose constructor copies them from LWJGL 3 once,
     * when that class first loads. That copy came out all false while the same query from this class
     * returns true, so the object is refreshed here from the authoritative side instead of trusting
     * the copy. Every capability GLSM and the culling backend consult afterwards reads this object,
     * which is why one stale copy turned compute, SSBO, image load/store and per-buffer blending off
     * for the whole session.
     */
    private static void refreshCompatibilityCapabilities() {
        try {
            final Object shim = org.lwjglx.opengl.GLContext.getCapabilities();
            final var real = GL.getCapabilities();
            int copied = 0;
            for (final java.lang.reflect.Field realField : real.getClass().getFields()) {
                final String name = realField.getName();
                if (!name.startsWith("GL_") && !name.startsWith("OpenGL")) {
                    continue;
                }
                try {
                    final java.lang.reflect.Field target = shim.getClass().getField(name);
                    target.setBoolean(shim, realField.getBoolean(real));
                    copied++;
                } catch (NoSuchFieldException absent) {
                    // This capability has no counterpart in the compatibility layer.
                } catch (IllegalAccessException notWritable) {
                    // The field exists but is not public on this build; leave it alone.
                }
            }
            LOGGER.info("Refreshed compatibility capabilities: shim={} copied={} of {} fields, "
                            + "shimOpenGL43={} shimCompute={} realOpenGL43={}",
                    Integer.valueOf(System.identityHashCode(shim)), Integer.valueOf(copied),
                    Integer.valueOf(real.getClass().getFields().length),
                    Boolean.valueOf(readShimFlag(shim, "OpenGL43")),
                    Boolean.valueOf(readShimFlag(shim, "GL_ARB_compute_shader")),
                    Boolean.valueOf(real.OpenGL43));
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Could not refresh the compatibility capability object", failure);
        }
    }

    /** Reads one flag back from the refreshed object, for the diagnostic line only. */
    private static boolean readShimFlag(Object shim, String name) {
        try {
            return shim.getClass().getField(name).getBoolean(shim);
        } catch (ReflectiveOperationException missing) {
            return false;
        }
    }

    /**
     * Describes what LWJGL 3 itself believes about the installed context.
     *
     * <p>Logged next to GLSM's own reading of the capability object, because the two disagreeing is
     * the whole diagnosis: either the installer never ran, or it ran and the object GLSM consults
     * was built before it did.
     */
    static String report() {
        try {
            var caps = GL.getCapabilities();
            return "OpenGL43=" + caps.OpenGL43 + " OpenGL45=" + caps.OpenGL45
                    + " compute=" + caps.GL_ARB_compute_shader
                    + " ssbo=" + caps.GL_ARB_shader_storage_buffer_object
                    + " imageLoadStore=" + caps.GL_ARB_shader_image_load_store;
        } catch (RuntimeException failure) {
            return "unavailable (" + failure.getClass().getSimpleName() + ")";
        }
    }

    /** Reports whether capabilities were installed for this thread's current context. */
    static boolean isContextCurrent() {
        try {
            return GL.getCapabilities().OpenGL11;
        } catch (IllegalStateException noCapabilities) {
            return false;
        }
    }
}
