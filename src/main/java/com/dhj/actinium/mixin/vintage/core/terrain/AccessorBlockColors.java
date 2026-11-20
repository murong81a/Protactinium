package com.dhj.actinium.mixin.vintage.core.terrain;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraftforge.fml.common.registry.RegistryDelegate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(BlockColors.class)
public interface AccessorBlockColors {
    @Accessor("blockColorMap")
    Map<RegistryDelegate<Block>, IBlockColor> getBlockColorMap();
}
