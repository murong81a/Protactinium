package com.dhj.actinium.gui;

import com.dhj.actinium.compat.sodium.ActiniumOptionHost;
import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

/**
 * Mod-list Config button factory: opens the RSO video options screen with
 * the mod list as its parent screen. Declared in mcmod.info under
 * "guiFactory".
 *
 * <p>1.12.2 spells this contract as {@code hasConfigGui()} plus
 * {@code createConfigGui(GuiScreen)}. The 1.10.2 Forge interface instead asks for the screen class
 * and instantiates it reflectively through its {@code (GuiScreen)} constructor, and also requires
 * {@code getHandlerFor} for the runtime category sub-screen. The screen class is returned rather
 * than a screen instance, so the constructor below is the 1.10.2 replacement for the removed
 * factory method.</p>
 */
public final class ActiniumGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return SodiumVideoOptionsScreen.class;
    }

    @Override
    public Set<IModGuiFactory.RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public IModGuiFactory.RuntimeOptionGuiHandler getHandlerFor(IModGuiFactory.RuntimeOptionCategoryElement element) {
        return null;
    }

    /**
     * 1.12.2 spelling of the config screen accessor, retained so code written against upstream
     * keeps compiling. The 1.10.2 mod list never calls it; it uses {@link #mainConfigGuiClass()}.
     */
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new SodiumVideoOptionsScreen(parentScreen, ActiniumOptionHost.shared().modOptions());
    }
}
