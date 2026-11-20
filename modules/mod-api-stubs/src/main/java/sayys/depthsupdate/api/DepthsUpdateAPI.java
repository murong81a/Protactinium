package sayys.depthsupdate.api;

/**
 * Compile-time declaration of Depths Update's world height query.
 *
 * <p>See {@code modules/mod-api-stubs/README.md}. Depths Update backports the extended world
 * height range; the renderer has to know the range so it can create sections outside the vanilla
 * {@code 0..15}, which is why the type is named in core code even though the mod has no 1.10.2
 * build. {@code DepthsUpdateCompat.IS_LOADED} gates the call and is false here, so the vanilla
 * range is used instead.</p>
 */
public final class DepthsUpdateAPI {

    private DepthsUpdateAPI() {}

    /**
     * The height range of a world.
     *
     * @param world the world
     * @return the range descriptor, or null when the mod is absent
     */
    public static HeightInfo getHeightInfo(net.minecraft.world.World world) {
        throw new UnsupportedOperationException("Depths Update is not present on this version");
    }
}
