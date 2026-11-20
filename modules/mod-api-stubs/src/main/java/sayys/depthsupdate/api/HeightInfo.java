package sayys.depthsupdate.api;

/**
 * Compile-time declaration of Depths Update's world height range descriptor.
 *
 * <p>See {@code modules/mod-api-stubs/README.md}. Only the accessors the renderer reads are
 * declared: whether the range is extended, and its bounds.</p>
 */
public final class HeightInfo {

    private HeightInfo() {}

    /** Whether the world uses a range other than the vanilla 0..256. */
    public boolean isExtended() {
        throw new UnsupportedOperationException("Depths Update is not present on this version");
    }

    /** Inclusive lowest block y. */
    public int minY() {
        throw new UnsupportedOperationException("Depths Update is not present on this version");
    }

    /** Exclusive highest block y. */
    public int maxY() {
        throw new UnsupportedOperationException("Depths Update is not present on this version");
    }
}
