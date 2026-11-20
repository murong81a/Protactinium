package com.dhj.actinium.compat.neverenoughanimations;

import com.dhj.actinium.render.ItemVertexAlphaOverride;
import com.dhj.actinium.render.ItemVertexAlphaOverrides;
import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizon.gtnhlib.compat.Mods;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Bridges NeverEnoughAnimation's GUI open/close fade (mod id {@code neverenoughanimations}) into
 * the fast lit item path.
 *
 * <p>The mod scales the alpha of every item vertex it draws while a GUI fades, through mixins on
 * {@code LightUtil.renderQuadColor}, the vertex buffer's {@code color} and {@code GlStateManager.color}.
 * The fast lit-item path bypasses the vanilla vertex pipeline, so the fade was either lost or baked
 * into the display list at the alpha of the frame that compiled it, leaving chest GUI items
 * invisible while that cache entry lived (issue #145). This source reports the fade multiplier so
 * the fast path steps aside for the animation.</p>
 *
 * <p>The mod's two entry points are reached through {@code MethodHandle}s because the mod has no
 * 1.10.2 build. The handles are bound only after {@link #install()} has confirmed the mod is
 * present, and a binding failure leaves the override unregistered: without it the fast path keeps
 * its previous behaviour, which is what upstream does when the mod is absent.</p>
 */
public final class NeverEnoughAnimationsAlphaOverride implements ItemVertexAlphaOverride {

    private static final String NEA_CLASS = "com.cleanroommc.neverenoughanimations.NEA";

    private final MethodHandle isAnimating;
    private final MethodHandle openValue;

    private NeverEnoughAnimationsAlphaOverride(MethodHandle isAnimating, MethodHandle openValue) {
        this.isAnimating = isAnimating;
        this.openValue = openValue;
    }

    /**
     * Installs the override when NeverEnoughAnimation is loaded.
     *
     * <p>Must be called from the host mod's init phase. The mod's class is bound only once the mod
     * is known to be present, so an absent mod never triggers a class load.</p>
     */
    public static void install() {
        if (!Mods.NEVERENOUGHANIMATIONS) {
            return;
        }

        try {
            Class<?> nea = Class.forName(NEA_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle isAnimating = lookup.findStatic(nea, "isCurrentGuiAnimating",
                    MethodType.methodType(boolean.class));
            MethodHandle openValue = lookup.findStatic(nea, "getCurrentOpenAnimationValue",
                    MethodType.methodType(float.class));

            ItemVertexAlphaOverrides.register(new NeverEnoughAnimationsAlphaOverride(isAnimating, openValue));
            ActiniumRuntime.logger().info("NeverEnoughAnimation compatibility layer enabled");
        } catch (ReflectiveOperationException e) {
            ActiniumRuntime.logger().error(
                    "NeverEnoughAnimation is loaded but its fade API could not be bound; the fast lit item path"
                            + " will not follow GUI fades", e);
        }
    }

    @Override
    public float getAlphaMultiplier() {
        try {
            boolean animating = (boolean) this.isAnimating.invoke();
            return animating ? (float) this.openValue.invoke() : 1.0F;
        } catch (Throwable t) {
            // Report "no fade" rather than failing a draw call: a missing fade is a visual
            // difference, an exception here would be a crash inside the item renderer.
            ActiniumRuntime.logger().warn("NeverEnoughAnimation fade query failed", t);
            return 1.0F;
        }
    }
}
