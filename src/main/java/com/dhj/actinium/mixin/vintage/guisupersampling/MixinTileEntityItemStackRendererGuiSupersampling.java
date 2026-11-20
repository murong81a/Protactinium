package com.dhj.actinium.mixin.vintage.guisupersampling;

import com.dhj.actinium.render.GuiBlockSupersampling;
import com.dhj.actinium.render.RenderingState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Supersamples the item atlas for stacks whose GUI model comes from a block entity renderer. */
@Mixin(TileEntityItemStackRenderer.class)
public class MixinTileEntityItemStackRendererGuiSupersampling {

    @WrapMethod(method = "func_179022_a(Lnet/minecraft/item/ItemStack;)V")
    private void actinium$supersampleGuiBlock(ItemStack stack, Operation<Void> original) {
        final boolean previousItem = RenderingState.isRenderingGuiItem();
        RenderingState.setRenderingGuiItem(true);
        final int previousSamples = GuiBlockSupersampling.begin();
        try {
            original.call(stack);
        } finally {
            GuiBlockSupersampling.end(previousSamples);
            RenderingState.setRenderingGuiItem(previousItem);
        }
    }
}
