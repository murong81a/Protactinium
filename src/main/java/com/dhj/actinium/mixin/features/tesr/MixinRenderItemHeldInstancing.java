package com.dhj.actinium.mixin.features.tesr;

import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderItem.class)
public abstract class MixinRenderItemHeldInstancing {
    // func_184394_a is renderItemModel, the per-item entry 1.10.2 funnels every held-item draw
    // through. Upstream wrapped the call to renderItem(ItemStack, IBakedModel), which this method
    // reaches indirectly, so the wrap point moved to the method itself.
    @WrapMethod(method = "func_184394_a")
    private void actinium$instanceHeldItem(ItemStack stack, IBakedModel model, TransformType transform,
                                           boolean leftHand, Operation<Void> original) {
        final RenderItem renderer = (RenderItem) (Object) this;
        DroppedItemInstancer.renderHeldItem(renderer::renderQuads, renderer.itemColors, stack, model, transform,
            () -> original.call(stack, model, transform, leftHand));
    }
}
