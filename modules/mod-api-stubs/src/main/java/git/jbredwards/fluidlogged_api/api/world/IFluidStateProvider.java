package git.jbredwards.fluidlogged_api.api.world;

import git.jbredwards.fluidlogged_api.api.util.FluidState;

/**
 * Compile-time declaration of the Fluidlogged API's fluid-state provider.
 *
 * <p>See {@code modules/mod-api-stubs/README.md}. {@code ActiniumBlockAccess} extends this so the
 * chunk builder can ask for a logged fluid state through the same interface it uses for block
 * states. The mod does not exist on 1.10.2; the interface is declared for compilation and the
 * whole path is gated behind {@code FluidloggedCompat.IS_LOADED}.</p>
 */
public interface IFluidStateProvider {

    /**
     * The fluid state logged at a position.
     *
     * <p>The coordinate form is what a frozen world section answers, because a section does not
     * carry a mutable position.</p>
     */
    FluidState getFluidState(int x, int y, int z);

    /** Position form, which the block-access type exposes. */
    default FluidState getFluidState(net.minecraft.util.math.BlockPos pos) {
        return getFluidState(pos.getX(), pos.getY(), pos.getZ());
    }

    /** The world this provider reads from. */
    net.minecraft.world.World getWorld();
}
