package git.jbredwards.fluidlogged_api.api.block;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Compile-time declaration of the Fluidlogged API's "a block can hold fluid" marker.
 *
 * <p>See {@code modules/mod-api-stubs/README.md}. The chunk builder asks an
 * {@code IFluidloggable} block whether a logged fluid should be drawn; the mod does not exist on
 * 1.10.2, so the interface is declared here and the call site is unreachable behind
 * {@code FluidloggedCompat.IS_LOADED}.</p>
 */
public interface IFluidloggable {

    /**
     * Whether the fluid logged inside this block at this position should be rendered.
     *
     * @param world the world being meshed
     * @param pos the block position
     * @param state the block state at that position
     * @param fluidState the fluid logged there
     * @return true when the fluid layer should be drawn
     */
    boolean shouldFluidRender(IBlockAccess world, BlockPos pos, IBlockState state, FluidState fluidState);
}
