package git.jbredwards.fluidlogged_api.api.util;

/**
 * Compile-time declaration of the Fluidlogged API's per-position fluid state.
 *
 * <p>See {@code modules/mod-api-stubs/README.md} for why these declarations exist. The short
 * version: the chunk pipeline stores fluid states per section, so the core renderer names this
 * type even though the mod that provides it has no 1.10.2 build. Every call site sits behind
 * {@code FluidloggedCompat.IS_LOADED}, which reads the mod list and is false here, so none of these
 * members is ever reached at runtime.</p>
 *
 * <p>Only the members the ported code names are declared, and each one reports clearly if it is
 * ever reached. That keeps a missing member a compile error rather than a silent no-op.</p>
 */
public class FluidState {

    /** The empty fluid state, meaning "no fluid logged at this position". */
    public static final FluidState EMPTY = new FluidState();

    /** Wraps a block state as a fluid state. */
    public static FluidState of(net.minecraft.block.state.IBlockState state) {
        throw new UnsupportedOperationException("Fluidlogged API is not present on this version");
    }

    /** The block state this fluid state renders as. */
    public net.minecraft.block.state.IBlockState getState() {
        throw new UnsupportedOperationException("Fluidlogged API is not present on this version");
    }
}
