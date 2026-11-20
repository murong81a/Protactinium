package com.gtnewhorizons.angelica.rendering.items;

import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Writes one list of baked quads into a vertex buffer.
 *
 * <p>Production passes {@code RenderItem::renderQuads}, so the capture goes through the same method
 * the vanilla draw calls and keeps whatever a mod injects into it or into the vertex call inside it.
 * The capture is written against this signature instead of against {@code RenderItem} itself because
 * a {@code RenderItem} cannot be built outside a client: its constructor registers every vanilla
 * item against a model manager that only exists after a resource reload.</p>
 */
@FunctionalInterface
public interface ItemQuadWriter {

    /**
     * Writes the quads into {@code buffer}.
     *
     * @param color colour to multiply into every vertex, or {@code -1} to resolve each quad's tint
     *              index through the item colour table
     * @param stack stack the tint indices resolve against, or an empty stack from
     *              {@link net.minecraft.item.ItemStackCompat#empty()} to keep the baked colours.
     *              It is only consulted when {@code color} is not {@code -1} and the quad carries a
     *              tint index.
     */
    void renderQuads(VertexBuffer buffer, List<BakedQuad> quads, int color, ItemStack stack);
}
