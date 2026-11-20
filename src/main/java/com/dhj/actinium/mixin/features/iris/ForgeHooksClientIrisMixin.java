package com.dhj.actinium.mixin.features.iris;

import com.dhj.actinium.config.ActiniumRuntimeOptions;
import com.dhj.actinium.render.FastLitItemDisplayListCache;
import com.dhj.actinium.render.ItemVertexAlphaOverrides;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import com.dhj.actinium.render.terrain.sprite.SpriteUtil;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Takes over the simple lit-item draw to use the cached display list and the raw append path.
 *
 * <p>Targets {@code RenderItem.renderModel}, which is where 1.10.2 appends an item model's quads.
 * Upstream hooks {@code ForgeHooksClient.renderLitItem}, a method that does not exist in 1.10.2 and
 * was not introduced until 1.11, so the injection point had to move rather than the feature be
 * dropped: the hook is what makes the cached and raw-append item paths reachable at all.</p>
 */
@Mixin(value = RenderItem.class, remap = false)
public abstract class ForgeHooksClientIrisMixin {
    @Unique
    private static final ThreadLocal<List<BakedQuad>> actinium$fastLitItemQuads =
            ThreadLocal.withInitial(ArrayList::new);

    @Inject(
        // The SRG name: the target is a production client, where the annotation processor does not
        // remap a plain method selector for this mixin.
        method = "func_175045_a(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void actinium$renderSimpleLitItem(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) {
        final RenderItem renderItem = (RenderItem) (Object) this;
        if (IrisApi.getInstance().isShaderPackInUse()) {
            return;
        }

        if (!ActiniumRuntimeOptions.useFastLitItemRendering()) {
            return;
        }

        List<BakedQuad> quads = actinium$fastLitItemQuads.get();
        quads.clear();

        for (EnumFacing facing : EnumFacing.VALUES) {
            quads.addAll(model.getQuads(null, facing, 0L));
        }
        quads.addAll(model.getQuads(null, null, 0L));

        if (quads.isEmpty()) {
            ci.cancel();
            return;
        }

        if (!actinium$isSimpleLitItemModel(quads)) {
            quads.clear();
            return;
        }

        // A registered mod is scaling item vertex alpha right now (NeverEnoughAnimation's GUI
        // open/close fade, issue #145). Both shortcuts below assume the vertex colours are a pure
        // function of the baked quad data: the raw append writes the quad bytes unchanged and drops
        // the scaling, and the display list bakes the alpha of the compile frame into a cache that
        // glCallList then replays, which left chest GUI items permanently transparent. Drawing the
        // quads through renderQuads keeps the scaling visible without caching it; falling through to
        // Forge's own renderLitItem instead would hand the frame's lightmap and lighting state to a
        // renderer Actinium does not own, which darkened the GUI.
        boolean vertexAlphaScaled = ItemVertexAlphaOverrides.isActive();

        try {
            Tessellator tessellator = Tessellator.getInstance();
            FastLitItemDisplayListCache.CachedDisplayList cached = !vertexAlphaScaled
                    && ActiniumRuntimeOptions.useFastLitItemDisplayLists()
                    ? FastLitItemDisplayListCache.getOrCompile(renderItem, model, quads, color, stack)
                    : null;
            if (cached != null) {
                cached.render();
            } else if (!vertexAlphaScaled && actinium$canAppendRawItemQuads(quads, color)) {
                VertexBuffer buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
                actinium$appendRawItemQuads(buffer, quads);
                tessellator.draw();
            } else {
                VertexBuffer buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
                renderItem.renderQuads(buffer, quads, color, stack);
                tessellator.draw();
            }
            ci.cancel();
        } finally {
            quads.clear();
        }
    }

    @Unique
    private static boolean actinium$isSimpleLitItemModel(List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            if (!quad.shouldApplyDiffuseLighting()) {
                return false;
            }

            if (quad.getFormat() != DefaultVertexFormats.ITEM && quad.getFormat().hasUvOffset(1)) {
                return false;
            }
        }

        return true;
    }

    @Unique
    private static boolean actinium$canAppendRawItemQuads(List<BakedQuad> quads, int color) {
        if (color != -1) {
            return false;
        }

        for (BakedQuad quad : quads) {
            if (!DefaultVertexFormats.ITEM.equals(quad.getFormat()) || quad.hasTintIndex()) {
                return false;
            }
        }

        return true;
    }

    @Unique
    private static void actinium$appendRawItemQuads(VertexBuffer buffer, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.getSprite();
            if (sprite != null) {
                SpriteUtil.markSpriteActive(sprite);
            }
            buffer.addVertexData(quad.getVertexData());
        }
    }
}
