package com.dhj.actinium.startup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loads a world at startup, for measurement and for verifying that the terrain renderer actually
 * runs rather than merely initializes.
 *
 * <p>Off unless {@code -Dprotactinium.autoloadWorld=<save directory name>} is set, which keeps it out
 * of normal use. The title screen is not evidence about the renderer: the panorama is drawn by a
 * different path, so the batched chunk renderer, the streaming buffers and the section manager are
 * all untouched while the main menu is up. Loading a world is the only way to see them work, and
 * doing it by hand is not repeatable.</p>
 *
 * <p>The world is opened from the title screen's own button action, so the client follows exactly
 * the path a player would take: options are loaded, the world is opened, the renderer is created and
 * sections are built.</p>
 */
public final class StartupWorldLoader {

    private static final Logger LOGGER = LogManager.getLogger("Protactinium");

    /** Save directory to open, or null when automatic loading is disabled. */
    private static final String WORLD = System.getProperty("protactinium.autoloadWorld", "").trim();

    /** Whether the attempt has already been made, so the check runs once. */
    private static boolean attempted;

    /**
     * Opens the world on the first client tick after the title screen initializes.
     *
     * <p>The title screen is shown before {@code Minecraft}'s loading screen exists, and launching
     * the integrated server from {@code initGui} therefore dereferences a null loading screen and
     * throws. One tick later startup has finished and the launch follows the normal path.</p>
     */
    private static final Object TICK_HANDLER = new Object() {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            MinecraftForge.EVENT_BUS.unregister(TICK_HANDLER);
            launch();
        }
    };

    private StartupWorldLoader() {}

    /** Returns whether automatic world loading was requested. */
    public static boolean isRequested() {
        return !WORLD.isEmpty();
    }

    /**
     * Opens the configured world if automatic loading was requested.
     *
     * <p>Called from the title screen once it has been shown, because the world selection list is
     * only populated after that point. A failure is logged and does not exit the client: a loaded
     * title screen is still a useful measurement state, and the message says which world was missing.</p>
     *
     * @param menu the title screen that is about to be displayed
     */
    public static void loadIfRequested(GuiMainMenu menu) {
        if (attempted || !isRequested()) {
            return;
        }
        attempted = true;
        MinecraftForge.EVENT_BUS.register(TICK_HANDLER);
    }

    private static void launch() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                LOGGER.warn("Automatic world loading skipped: no client instance");
                return;
            }
            LOGGER.info("Automatic world loading requested, opening save '{}'", WORLD);
            // The name is resolved through the save loader rather than by building a path, so the
            // world's own metadata (dimension, game mode, whether cheats were allowed) is honoured.
            net.minecraft.world.storage.ISaveFormat saves = minecraft.getSaveLoader();
            net.minecraft.world.storage.WorldSummary summary = null;
            for (net.minecraft.world.storage.WorldSummary candidate : saves.getSaveList()) {
                if (candidate.getFileName().equals(WORLD)) {
                    summary = candidate;
                    break;
                }
            }
            if (summary == null) {
                LOGGER.error("Automatic world loading failed: no save directory named '{}'", WORLD);
                return;
            }
            minecraft.launchIntegratedServer(summary.getFileName(), summary.getDisplayName(), null);
        } catch (net.minecraft.client.AnvilConverterException checked) {
            // The save list cannot be read at all, which is worth reporting but is not a reason to
            // take the client down: the title screen still works and still shows the renderer state.
            LOGGER.error("Automatic world loading failed: the save list could not be read", checked);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error("Automatic world loading failed", failure);
        }
    }
}
