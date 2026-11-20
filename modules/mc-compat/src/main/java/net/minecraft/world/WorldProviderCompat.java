package net.minecraft.world;

/**
 * Back-implementation of {@code WorldProvider.hasSkyLight()}, which Minecraft gained in 1.11.
 *
 * <p>1.11 renamed the sky test. Before that, 1.10.2 exposes exactly the same fact as the
 * {@code hasNoSky()} accessor, which reports whether the dimension draws a sky at all. The two are
 * the same predicate with inverted polarity, and every 1.10.2 call site that asks "is there sky
 * light here" reads {@code !provider.hasNoSky()}.</p>
 *
 * <p>This is a real translation of the value, not a constant: a sky-less dimension still returns
 * false and a normal overworld still returns true.</p>
 */
public final class WorldProviderCompat {

    private WorldProviderCompat() {}

    /**
     * The 1.11 sky-light test, expressed with the 1.10.2 accessor it replaced.
     *
     * @param provider the dimension provider to query, must not be null
     * @return true when the dimension has sky light
     */
    public static boolean hasSkyLight(WorldProvider provider) {
        return !provider.hasNoSky();
    }
}
