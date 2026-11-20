package com.gtnewhorizons.angelica.glsm;

/**
 * Legacy OpenGL constants that LWJGL 3 no longer declares.
 *
 * <p>LWJGL 2's {@code org.lwjgl.opengl.GL14} carried a handful of GL 1.4 aliases that LWJGL 3's
 * rewritten bindings dropped, because the values are identical to constants LWJGL 3 declares
 * elsewhere (or, for {@code GL_BLEND_COLOR}, in an extension class). Upstream Actinium compiles
 * against LWJGL 2 and therefore says {@code GLConstants.GL_BLEND_COLOR}; this port runs on LWJGL 3, so
 * the same values are named here once instead of being spread through the query and state code.</p>
 *
 * <p>Only aliases whose numeric value is fixed by the GL specification belong here. The values
 * below are the ones from {@code glcorearb.h}; they are not driver dependent.</p>
 */
public final class GLConstants {

    private GLConstants() {}

    /**
     * {@code GL_BLEND_COLOR} (0x8005). LWJGL 3 declares it only as
     * {@code ARBImaging.GL_BLEND_COLOR}; GL 1.4 core made it a core query.
     */
    public static final int GL_BLEND_COLOR = 0x8005;

    /**
     * {@code GL_BLEND_EQUATION} (0x8009), the GL 1.4 spelling of what GL 2.0 calls
     * {@code GL_BLEND_EQUATION_RGB}. Same value, so state lookups may use either name.
     */
    public static final int GL_BLEND_EQUATION = 0x8009;

    /**
     * {@code GL_CURRENT_SECONDARY_COLOR} (0x8459). Declared by LWJGL 3's GL14, kept as an alias
     * here only so call sites read uniformly next to the two above.
     */
    public static final int GL_CURRENT_SECONDARY_COLOR = 0x8459;
}
