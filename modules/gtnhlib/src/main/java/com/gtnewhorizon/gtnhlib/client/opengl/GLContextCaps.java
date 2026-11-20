package com.gtnewhorizon.gtnhlib.client.opengl;

// 1.10.2 compatibility layer: OpenGL capability queries.
//
// Upstream GTNHLib asks LWJGL 2's GLContext for a ContextCapabilities object and reads
// boolean fields off it (caps.OpenGL30, caps.GL_ARB_buffer_storage, ...). That whole class
// does not exist in LWJGL 3, which instead exposes capabilities through GL.getCapabilities()
// and the GLCapabilities interface.
//
// This port runs on the user's 1.10.2 instance, which launches through RetroFuturaBootstrap
// with LWJGL 3.3.3 (see lwjgl3ify-1102). So capability lookups are answered from LWJGL 3 and
// the class keeps the field names upstream call sites already use, which keeps those call
// sites unchanged and readable next to upstream.
//
// Every field is resolved lazily on first read rather than in a static initialiser: GLCaps is
// loaded during startup, and on some drivers GL.getCapabilities() is not valid until the
// context is current. A thrown exception here would take the renderer down with it, so a
// failed lookup degrades to "not supported" and logs.

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class GLContextCaps {

    private static final Logger LOGGER = LogManager.getLogger("GTNHLib");

    private static volatile GLCapabilities caps;
    private static volatile boolean queryFailed;

    private GLContextCaps() {}

    private static GLCapabilities caps() {
        GLCapabilities local = caps;
        if (local != null) {
            return local;
        }
        if (queryFailed) {
            return null;
        }
        synchronized (GLContextCaps.class) {
            if (caps == null && !queryFailed) {
                try {
                    caps = GL.getCapabilities();
                } catch (IllegalStateException e) {
                    // Thrown by LWJGL when no context is current. Not fatal: the feature
                    // queries below then answer false and the caller takes its fallback.
                    queryFailed = true;
                    LOGGER.warn("GL capabilities unavailable, assuming the minimum feature set", e);
                }
            }
            return caps;
        }
    }

    private static boolean has(boolean value) {
        return value;
    }

    /** GL 3.0 core. */
    public static boolean OpenGL30() {
        GLCapabilities c = caps();
        return c != null && has(c.OpenGL30);
    }

    /** GL_ARB_framebuffer_object. */
    public static boolean framebufferObject() {
        GLCapabilities c = caps();
        return c != null && (c.OpenGL30 || c.GL_ARB_framebuffer_object);
    }

    /** GL_EXT_framebuffer_object. */
    public static boolean extFramebufferObject() {
        GLCapabilities c = caps();
        return c != null && c.GL_EXT_framebuffer_object;
    }

    /** GL 4.2 or GL_ARB_texture_storage. */
    public static boolean texStorage() {
        GLCapabilities c = caps();
        return c != null && (c.OpenGL42 || c.GL_ARB_texture_storage);
    }

    /** GL 4.4 or GL_ARB_buffer_storage. */
    public static boolean bufferStorage() {
        GLCapabilities c = caps();
        return c != null && (c.OpenGL44 || c.GL_ARB_buffer_storage);
    }

    /** GL 3.2 or GL_ARB_sync. */
    public static boolean sync() {
        GLCapabilities c = caps();
        return c != null && (c.OpenGL32 || c.GL_ARB_sync);
    }

    /** GL 4.3 or GL_ARB_multi_draw_indirect. */
    public static boolean multiDrawIndirect() {
        GLCapabilities c = caps();
        return c != null && (c.OpenGL43 || c.GL_ARB_multi_draw_indirect);
    }

    /** GL 4.6 or GL_ARB_direct_state_access. */
    public static boolean directStateAccess() {
        GLCapabilities c = caps();
        return c != null && (c.OpenGL46 || c.GL_ARB_direct_state_access);
    }

    /** GL 3.1 (needed for uniform buffer objects). */
    public static boolean OpenGL31() {
        GLCapabilities c = caps();
        return c != null && c.OpenGL31;
    }

    /** GL 4.3 (compute shaders, shader storage buffers). */
    public static boolean OpenGL43() {
        GLCapabilities c = caps();
        return c != null && c.OpenGL43;
    }

    /** GL_ARB_vertex_array_object. */
    public static boolean vertexArrayObject() {
        GLCapabilities c = caps();
        return c != null && (c.OpenGL30 || c.GL_ARB_vertex_array_object);
    }

    /** GL_NV_vertex_array_range, the only path that needs client-side array ranges. */
    public static boolean vertexArrayRange() {
        GLCapabilities c = caps();
        return c != null && c.GL_NV_vertex_array_range;
    }
}
