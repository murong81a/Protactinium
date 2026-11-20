package com.dhj.actinium.mixin.vintage.core.terrain;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import dhj.embeddedt.embeddium.impl.model.quad.BakedQuadView;
import dhj.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import dhj.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFlags;
import dhj.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import dhj.embeddedt.embeddium.impl.util.ModelQuadUtil;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.dhj.actinium.render.terrain.compile.light.VintageDiffuseProvider;

@Mixin(BakedQuad.class)
public abstract class MixinBakedQuad implements BakedQuadView {
    @Shadow(remap = false)
    @Final
    protected EnumFacing field_178214_c;

    @Shadow(remap = false)
    @Final
    protected boolean applyDiffuseLighting;

    @Shadow(remap = false)
    public abstract int[] func_178209_a();

    @Shadow(remap = false)
    public abstract VertexFormat getFormat();

    @Shadow(remap = false)
    @Final
    protected TextureAtlasSprite field_187509_d;
    @Shadow(remap = false)
    @Final
    protected int field_178213_b;
    @Unique
    private int flags;

    @Unique
    private int normal;

    @Unique
    private ModelQuadFacing normalFace;

    @Override
    public ModelQuadFacing getNormalFace() {
        var field_178214_c = this.normalFace;
        if (field_178214_c == null) {
            this.normalFace = field_178214_c = ModelQuadUtil.findNormalFace(getComputedFaceNormal());
        }
        return field_178214_c;
    }

    @Override
    public int getFlags() {
        int f = this.flags;
        if ((f & ModelQuadFlags.IS_POPULATED) == 0) {
            this.flags = f = ModelQuadFlags.getQuadFlags(this, getLightFace(), f);
        }
        return f;
    }

    @Override
    public void addFlags(int flags) {
        this.flags |= flags;
    }

    @Override
    public boolean hasShade() {
        return this.applyDiffuseLighting;
    }

    @Override
    public int getVerticesCount() {
        return this.func_178209_a().length / this.getFormat().getIntegerSize();
    }

    @Override
    public @Nullable SpriteTransparencyLevel getTransparencyLevel() {
        return null;
    }

    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.func_178209_a()[idx * getFormat().getIntegerSize()]);
    }

    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.func_178209_a()[idx * getFormat().getIntegerSize() + 1]);
    }

    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.func_178209_a()[idx * getFormat().getIntegerSize() + 2]);
    }

    @Override
    public int getColor(int idx) {
        var format = getFormat();
        int offset = format.getColorOffset();
        if (offset >= 0) {
            return this.func_178209_a()[idx * format.getIntegerSize() + (offset / 4)];
        } else {
            return 0;
        }
    }

    @Override
    public Object celeritas$getSprite() {
        return this.field_187509_d;
    }

    @Override
    public float getTexU(int idx) {
        var format = getFormat();
        int offset = format.getUvOffsetById(0);
        return Float.intBitsToFloat(this.func_178209_a()[idx * format.getIntegerSize() + (offset / 4)]);
    }

    @Override
    public float getTexV(int idx) {
        var format = getFormat();
        int offset = format.getUvOffsetById(0);
        return Float.intBitsToFloat(this.func_178209_a()[idx * format.getIntegerSize() + (offset / 4) + 1]);
    }

    @Override
    public int getLight(int idx) {
        var format = getFormat();
        if (format.hasUvOffset(1)) {
            int offset = format.getUvOffsetById(1);
            return this.func_178209_a()[idx * format.getIntegerSize() + (offset / 4)];
        } else {
            return 0;
        }
    }

    @Override
    public int getForgeNormal(int idx) {
        var format = getFormat();
        int offset = format.getNormalOffset();
        if (offset >= 0) {
            return this.func_178209_a()[idx * format.getIntegerSize() + (offset / 4)];
        } else {
            return 0;
        }
    }

    @Override
    public ModelQuadFacing getLightFace() {
        // Handle mods not supplying a light field_178214_c
        var field_178214_c = this.field_178214_c;
        return field_178214_c == null ? ModelQuadFacing.POS_Y : VintageDiffuseProvider.fromEnumFacing(field_178214_c);
    }

    @Override
    public int getComputedFaceNormal() {
        int n = this.normal;
        if (n == 0) {
            this.normal = n = ModelQuadUtil.calculateNormal(this);
        }
        return n;
    }

    @Override
    public int getColorIndex() {
        return this.field_178213_b;
    }
}

