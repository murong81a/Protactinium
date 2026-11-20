package net.minecraft.entity;

/**
 * Back-implementation of {@code Entity.getBrightnessForRender()}, which Minecraft gained in 1.11.
 *
 * <p>1.10.2 has only the partial-tick form, {@code getBrightnessForRender(float)}. The no-argument
 * 1.11 call means "brightness at the current frame", which is the partial-tick form evaluated with
 * the frame's partial tick. This class supplies that reading without patching the game class.</p>
 *
 * <p>Why not call the float form with 1.0F at every site: at partial tick 1.0 the expression
 * collapses to the block's static brightness, which loses the interpolation between the previous
 * and current entity position that the call exists to provide. Reading
 * {@code Minecraft.getMinecraft().getRenderPartialTicks()} keeps it.</p>
 */
public final class EntityBrightnessCompat {

    private EntityBrightnessCompat() {}

    /**
     * The 1.11 no-argument brightness query, evaluated in 1.10.2's terms.
     *
     * @param entity the entity to query, must not be null
     * @return packed lightmap coordinates, in the same encoding the float form returns
     */
    public static int getBrightnessForRender(Entity entity) {
        float partialTicks = net.minecraft.client.Minecraft.getMinecraft().getRenderPartialTicks();
        return entity.getBrightnessForRender(partialTicks);
    }
}
