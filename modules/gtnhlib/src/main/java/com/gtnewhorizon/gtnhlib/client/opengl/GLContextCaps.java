package com.gtnewhorizon.gtnhlib.client.opengl;

// 1.10.2 compatibility layer: OpenGL capability queries.
//
// Upstream GTNHLib asks LWJGL 2's GLContext for a ContextCapabilities object and reads
// boolean fields off it (caps.OpenGL30, caps.GL_ARB_buffer_storage, ...). That whole class
// does not exist in LWJGL 3, which instead exposes capabilities through GL.getCapabilities()
// and the GLCapabilities interface.
//
// This port runs on the user's 1.10.2 instance, which launches through RetroFuturaBootstrap
// with LWJGL 3.3.3 (see lwjgl3ify-1102). Capability lookups go through lwjgl3ify own compat
// layer, org.lwjglx.opengl.GLContext, whose ContextCapabilities carries the same boolean field
// names upstream call sites already use.
//
// The lookup must NOT call org.lwjgl.opengl.GL.getCapabilities(): lwjgl3ify rewrites every
// org.lwjgl.opengl reference to org.lwjglx.opengl, and this trimmed port ships no aggregate GL
// class there, so that path dies with NoClassDefFoundError: org/lwjglx/opengl/GL on the first
// first-person model draw.
//
// Every field is resolved lazily on first read rather than in a static initialiser: GLCaps is
// loaded during startup, and on some drivers GL.getCapabilities() is not valid until the
// context is current. A thrown exception here would take the renderer down with it, so a
// failed lookup degrades to "not supported" and logs.

import org.lwjglx.opengl.ContextCapabilities;
import org.lwjglx.opengl.GLContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class GLContextCaps {

    private static final Logger LOGGER = LogManager.getLogger("GTNHLib");

    private static volatile ContextCapabilities caps;
    private static volatile boolean queryFailed;

    private GLContextCaps() {}

    private static ContextCapabilities caps() {
        ContextCapabilities local = caps;
        if (local != null) {
            return local;
        }
        if (queryFailed) {
            return null;
        }
        synchronized (GLContextCaps.class) {
            if (caps == null && !queryFailed) {
                try {
                    caps = GLContext.getCapabilities();
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
        ContextCapabilities c = caps();
        return c != null && has(c.OpenGL30);
    }

    /** GL_ARB_framebuffer_object. */
    public static boolean framebufferObject() {
        ContextCapabilities c = caps();
        return c != null && (c.OpenGL30 || c.GL_ARB_framebuffer_object);
    }

    /** GL_EXT_framebuffer_object. */
    public static boolean extFramebufferObject() {
        ContextCapabilities c = caps();
        return c != null && c.GL_EXT_framebuffer_object;
    }

    /** GL 4.2 or GL_ARB_texture_storage. */
    public static boolean texStorage() {
        ContextCapabilities c = caps();
        return c != null && (c.OpenGL42 || c.GL_ARB_texture_storage);
    }

    /** GL 4.4 or GL_ARB_buffer_storage. */
    public static boolean bufferStorage() {
        ContextCapabilities c = caps();
        return c != null && (c.OpenGL44 || c.GL_ARB_buffer_storage);
    }

    /** GL 3.2 or GL_ARB_sync. */
    public static boolean sync() {
        ContextCapabilities c = caps();
        return c != null && (c.OpenGL32 || c.GL_ARB_sync);
    }

    /** GL 4.3 or GL_ARB_multi_draw_indirect. */
    public static boolean multiDrawIndirect() {
        ContextCapabilities c = caps();
        return c != null && (c.OpenGL43 || c.GL_ARB_multi_draw_indirect);
    }

    /** GL 4.5 or GL_ARB_direct_state_access (DSA became core in 4.5). */
    public static boolean directStateAccess() {
        ContextCapabilities c = caps();
        return c != null && (c.OpenGL45 || c.GL_ARB_direct_state_access);
    }

    /** GL 3.1 (needed for uniform buffer objects). */
    public static boolean OpenGL31() {
        ContextCapabilities c = caps();
        return c != null && c.OpenGL31;
    }

    /** GL 4.3 (compute shaders, shader storage buffers). */
    public static boolean OpenGL43() {
        ContextCapabilities c = caps();
        return c != null && c.OpenGL43;
    }

    /** GL_ARB_vertex_array_object. */
    public static boolean vertexArrayObject() {
        ContextCapabilities c = caps();
        return c != null && (c.OpenGL30 || c.GL_ARB_vertex_array_object);
    }

    /** GL_NV_vertex_array_range, the only path that needs client-side array ranges. */
    public static boolean vertexArrayRange() {
        ContextCapabilities c = caps();
        return c != null && c.GL_NV_vertex_array_range;
    }
}
