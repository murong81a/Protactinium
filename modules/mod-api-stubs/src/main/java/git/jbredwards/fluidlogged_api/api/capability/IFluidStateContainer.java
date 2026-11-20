package git.jbredwards.fluidlogged_api.api.capability;

import git.jbredwards.fluidlogged_api.api.util.FluidState;

/**
 * Compile-time declaration of the Fluidlogged API's per-section fluid-state container.
 *
 * <p>See {@code modules/mod-api-stubs/README.md}. {@code FluidStateStorage} iterates this while
 * copying a cloned section; the copy runs only when the mod is present, which it never is on
 * 1.10.2.</p>
 */
public interface IFluidStateContainer {

    /** Visits every stored fluid state, passing a packed index and the state. */
    void forEach(FluidStateVisitor visitor);

    /** Packed index to local x coordinate. */
    int deserializeX(int index);

    /** Packed index to local y coordinate. */
    int deserializeY(int index);

    /** Packed index to local z coordinate. */
    int deserializeZ(int index);

    /** Callback for {@link #forEach}. */
    @FunctionalInterface
    interface FluidStateVisitor {
        void accept(int packedIndex, FluidState state);
    }
}
