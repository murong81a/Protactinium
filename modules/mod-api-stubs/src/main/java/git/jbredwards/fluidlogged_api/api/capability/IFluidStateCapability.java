package git.jbredwards.fluidlogged_api.api.capability;

import net.minecraftforge.common.capabilities.ICapabilityProvider;

/**
 * Compile-time declaration of the Fluidlogged API's chunk capability lookup.
 *
 * <p>See {@code modules/mod-api-stubs/README.md}. {@code FluidStateStorage} copies a section's
 * fluid data out of this capability while a chunk section is cloned; the copy runs only when
 * {@code FluidloggedCompat.IS_LOADED} is true, which it is not on 1.10.2.</p>
 */
public final class IFluidStateCapability {

    private IFluidStateCapability() {}

    /**
     * The capability attached to a chunk.
     *
     * @param provider the chunk
     * @return the capability, or null when the mod is absent
     */
    public static IFluidStateCapability get(ICapabilityProvider provider) {
        throw new UnsupportedOperationException("Fluidlogged API is not present on this version");
    }

    /**
     * The container holding fluid states for one 16-high layer of the chunk.
     *
     * @param sectionY the section index
     * @return the per-section container
     */
    public IFluidStateContainer getContainer(int sectionY) {
        throw new UnsupportedOperationException("Fluidlogged API is not present on this version");
    }
}
