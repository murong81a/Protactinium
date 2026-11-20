package com.dhj.actinium.mixin.vintage.gui;

import com.dhj.actinium.startup.StartupWorldLoader;
import com.gtnewhorizons.angelica.render.PanoramaRenderer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {
    // The members below are declared with their SRG names and remap disabled, because this class
    // targets a production client directly. Two facts force that style here:
    //
    //   1. panoramaTimer is int in 1.10.2 and was float in 1.12.2. A @Shadow with the wrong type is
    //      not merely inert: the annotation processor abandons the whole class, so this mixin used
    //      to emit no refmap entry at all.
    //   2. the annotation processor emits no refmap entry for an @Overwrite body, so renderSkybox
    //      has to be named by its SRG name to be found at apply time.
    @Shadow(remap = false)
    private int field_73979_m;

    /**
     * The panorama paths rendered on the title screen. Read at render time instead of
     * hard-coding the vanilla paths so mods that replace this field (e.g. Biomes O'
     * Plenty swaps it for its own panorama textures, issue #32) keep their title
     * screen background under the GTNH/Angelica skybox renderer.
     */
    @Shadow(remap = false)
    private static ResourceLocation[] field_73978_o;

    /**
     * @author Actinium
     * @reason Replace the vanilla panorama path with the GTNH/Angelica implementation.
     */
    /**
     * Opens the configured world once the title screen has had a chance to appear.
     *
     * <p>The save list is populated during the screen's own initialisation, so the hook runs at the
     * head of the game loop iteration that first draws it. Automatic loading is off unless
     * {@code -Dprotactinium.autoloadWorld=<save name>} is set; see {@link StartupWorldLoader}.</p>
     */
    // initGui is declared by GuiMainMenu itself, whereas drawScreen is inherited from GuiScreen and
    // therefore not a valid injection target here. It also runs after the save list is populated,
    // which is what makes the lookup succeed.
    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false)
    private void actinium$autoloadWorld(CallbackInfo ci) {
        StartupWorldLoader.loadIfRequested((GuiMainMenu) (Object) this);
    }
    @Overwrite(remap = false)
    public void func_73971_c(int mouseX, int mouseY, float partialTicks) {
        PanoramaRenderer.getInstance()
            .renderSkybox(
                this.field_73979_m,
                partialTicks,
                field_73978_o,
                this.mc,
                this.width,
                this.height,
                this.zLevel
            );
    }
}