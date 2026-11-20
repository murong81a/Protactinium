package team.chisel.ctm.client.asm;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;

/**
 * Compile-time declaration of CTM's layer-filter query.
 *
 * <p>See {@code modules/mod-api-stubs/README.md}. The chunk builder asks CTM whether a block's
 * model filters by render layer, so the type is named in core code; the mod has no 1.10.2 build
 * and {@code CtmLayerCompat} returns early when {@code Mods.CTM} is false, which it always is
 * here.</p>
 */
public final class CTMCoreMethods {

    private CTMCoreMethods() {}

    /**
     * Whether CTM handles this state for the given layer.
     *
     * @param state the block state being meshed
     * @param layer the layer being built
     * @return null when CTM does not handle the state, which the caller treats as "no filter"
     */
    public static Boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        throw new UnsupportedOperationException("CTM is not present on this version");
    }
}
