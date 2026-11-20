package com.dhj.actinium.mixin.mod.obscuretooltips;

import com.dhj.actinium.render.GuiGlStateBoundary;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Keeps world-render Iris state (armour item ids, enchant glint conditions) out of the armour-stand
 * entity pass that Obscure Tooltips runs inside the tooltip GUI surface.
 *
 * <p>The redirect intercepts one {@code renderImage} call so the armour preview can be drawn inside
 * a GL state boundary. Obscure Tooltips has no 1.10.2 build, so the mod's component and graphics
 * types are handled as {@code Object} and the call is forwarded through a {@code MethodHandle}
 * bound on first use. The mixin itself still targets the mod's renderer by name, so it is simply
 * skipped when the mod is absent.</p>
 */
@Pseudo
@Mixin(targets = "dev.obscuria.tooltips.client.TooltipRenderer", remap = false)
public abstract class MixinTooltipRenderer {

    /** The component method the redirect forwards to, resolved lazily from the first argument. */
    private static volatile MethodHandle renderImage;
    private static volatile Class<?> tooltipComponentInterface;
    private static volatile Class<?> armorPreviewClass;

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/component/TooltipComponent;renderImage"
                + "(Lnet/minecraft/client/gui/FontRenderer;IILdev/obscuria/tooltips/client/render/GuiGraphics;)V"
        ),
        remap = false
    )
    private static void actinium$renderImageWithEntitySurface(
        Object component,
        FontRenderer font,
        int x,
        int y,
        Object graphics
    ) {
        if (!isArmorPreview(component)) {
            invokeRenderImage(component, font, x, y, graphics);
            return;
        }
        GuiGlStateBoundary.EntitySurfaceState surface = GuiGlStateBoundary.beginEntitySurface();
        try {
            invokeRenderImage(component, font, x, y, graphics);
        } finally {
            surface.restore();
        }
    }

    private static boolean isArmorPreview(Object component) {
        Class<?> preview = armorPreviewClass;
        if (preview == null) {
            try {
                preview = Class.forName("dev.obscuria.tooltips.client.component.ArmorPreviewComponent");
                armorPreviewClass = preview;
            } catch (ClassNotFoundException e) {
                // The mod is present if this mixin ran, so a missing class means a different
                // version; treat every component as a plain one.
                com.dhj.actinium.runtime.ActiniumRuntime.logger().debug(
                        "Obscure Tooltips armor preview component not found; drawing all previews unguarded", e);
                return false;
            }
        }
        return preview.isInstance(component);
    }

    private static void invokeRenderImage(Object component, FontRenderer font, int x, int y, Object graphics) {
        try {
            MethodHandle handle = renderImage;
            if (handle == null) {
                Class<?> componentInterface = tooltipComponentInterface;
                if (componentInterface == null) {
                    componentInterface = Class.forName("dev.obscuria.tooltips.client.component.TooltipComponent");
                    tooltipComponentInterface = componentInterface;
                }
                handle = MethodHandles.lookup().findVirtual(componentInterface, "renderImage",
                        MethodType.methodType(void.class, FontRenderer.class, int.class, int.class,
                                Class.forName("dev.obscuria.tooltips.client.render.GuiGraphics")));
                renderImage = handle;
            }
            handle.invoke(component, font, x, y, graphics);
        } catch (ClassNotFoundException e) {
            com.dhj.actinium.runtime.ActiniumRuntime.logger().error(
                    "Obscure Tooltips tooltip types could not be resolved; the tooltip page will be left blank", e);
        } catch (Throwable t) {
            com.dhj.actinium.runtime.ActiniumRuntime.logger().error(
                    "Obscure Tooltips renderImage call failed", t);
        }
    }
}
