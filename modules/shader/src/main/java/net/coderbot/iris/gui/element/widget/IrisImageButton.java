package net.coderbot.iris.gui.element.widget;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import java.util.function.Consumer;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;

public class IrisImageButton extends IrisButton {
    private final ResourceLocation textureLocation;
    private final int xTexStart;
    private final int yTexStart;
    private final int yDiffTex;

    public IrisImageButton(int x, int y, int width, int height, int xTexStart, int yTexStart, int yDiffTex, ResourceLocation resourceLocation,Consumer<IrisButton> onPress) {
        super(x, y, width, height, "", onPress);

        this.textureLocation = resourceLocation;
        this.xTexStart = xTexStart;
        this.yTexStart = yTexStart;
        this.yDiffTex = yDiffTex;
    }


    // 1.10.2 GuiButton has no partialTicks parameter and names the position fields
    // xPosition / yPosition instead of x / y.
    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if(!this.visible) {
            return;
        }

        int yTex = this.yTexStart;
        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
        if(this.getHoverState(this.hovered) == 2) {
            yTex += this.yDiffTex;
        }

        GLSMBlend.glBlendColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.enableBlend();

        GLStateManager.enableTexture();
        mc.getTextureManager().bindTexture(this.textureLocation);

        Gui.drawModalRectWithCustomSizedTexture(this.xPosition, this.yPosition, this.xTexStart, yTex, width, height, 256, 256);

    }

}
