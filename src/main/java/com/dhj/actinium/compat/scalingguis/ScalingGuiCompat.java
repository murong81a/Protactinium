package com.dhj.actinium.compat.scalingguis;

import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizon.gtnhlib.compat.Mods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.lang.reflect.Constructor;

/**
 * Opens Scaling Guis' own configuration screen from this renderer's options page.
 *
 * <p>Scaling Guis has no 1.10.2 build, so its {@code GuiConfigSG} is created reflectively instead of
 * by naming the type. The contract is the one the mod publishes: a constructor taking the parent
 * screen and a page id. When the mod is absent the options button that calls this reports that the
 * screen is unavailable rather than opening a broken GUI.</p>
 */
public final class ScalingGuiCompat {

    private static final String CONFIG_GUI_CLASS = "spazley.scalingguis.gui.guiconfig.GuiConfigSG";
    private static final String MAIN_PAGE_FIELD = "MAIN_ID";

    private ScalingGuiCompat() {
    }

    /** Whether the configuration screen can be opened at all. */
    public static boolean isAvailable() {
        return Mods.SCALINGGUIS;
    }

    /**
     * Opens the mod's configuration screen, or logs why it could not be opened.
     *
     * @param parentScreen the screen to return to
     */
    public static void openConfigScreen(GuiScreen parentScreen) {
        if (!isAvailable()) {
            ActiniumRuntime.logger().warn("Scaling Guis is not installed; its config screen is unavailable");
            return;
        }

        try {
            Class<?> configGui = Class.forName(CONFIG_GUI_CLASS);
            Object pageId = configGui.getField(MAIN_PAGE_FIELD).get(null);
            Constructor<?> constructor = configGui.getConstructor(GuiScreen.class, int.class);
            GuiScreen screen = (GuiScreen) constructor.newInstance(parentScreen, pageId);
            Minecraft.getMinecraft().displayGuiScreen(screen);
        } catch (ReflectiveOperationException e) {
            // A missing page id or constructor means a version mismatch with the installed mod, not
            // a reason to kill the client: report it and leave the current screen open.
            ActiniumRuntime.logger().error(
                    "Scaling Guis is installed but its config screen could not be constructed", e);
        }
    }
}
