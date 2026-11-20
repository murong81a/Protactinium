package com.dhj.actinium.mixin.core.terrain;

import com.dhj.actinium.render.ProjectiveTexCoordBuffer;
import com.dhj.actinium.render.ProjectiveTexCoordWriter;
import com.dhj.actinium.render.vertex.DirectBufferAddress;
import com.dhj.actinium.render.vertex.FastVertexLayout;
import com.dhj.actinium.render.vertex.VertexWriters;
import com.gtnewhorizons.angelica.glsm.ITessellatorData;
import com.gtnewhorizons.angelica.rendering.tesr.SharedVertexLayout;
import net.coderbot.iris.celeritas.buffer.ShaderMaterialOverrideState;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;
import dhj.embeddedt.embeddium.api.shader.buffer.VertexBufferExtension;
import dhj.embeddedt.embeddium.api.shader.buffer.VanillaQuadContext;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin implements VertexBufferExtension, ProjectiveTexCoordBuffer, ITessellatorData {
    @Shadow(remap = false)
    private ByteBuffer field_179001_a;

    @Shadow(remap = false)
    private int field_178997_d;

    @Shadow(remap = false)
    private VertexFormat field_179011_q;

    @Shadow(remap = false)
    private int field_181678_g;

    @Shadow(remap = false)
    private VertexFormatElement field_181677_f;

    @Shadow(remap = false)
    private int field_179006_k;

    @Shadow(remap = false)
    private boolean field_179010_r;

    @Shadow(remap = false)
    private boolean field_78939_q;

    @Shadow(remap = false)
    private double field_179004_l;

    @Shadow(remap = false)
    private double field_179005_m;

    @Shadow(remap = false)
    private double field_179002_n;

    @Shadow(remap = false)
    public abstract void func_178965_a();

    @Invoker("nextVertexFormatIndex")
    protected abstract void actinium$nextVertexFormatIndex();

    @Unique
    private long actinium$bufferAddress;

    @Unique
    private List<VanillaQuadContext> actinium$quadContexts;

    @Unique
    private @Nullable VanillaQuadContext actinium$activeQuadContext;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void actinium$init(int bufferSizeIn, CallbackInfo ci) {
        this.actinium$quadContexts = new ArrayList<>();
        this.actinium$refreshBufferAddress();
    }

    @Inject(method = "growBuffer", at = @At("TAIL"))
    private void actinium$growBuffer(int increaseAmount, CallbackInfo ci) {
        this.actinium$refreshBufferAddress();
    }

    @Unique
    private void actinium$refreshBufferAddress() {
        this.actinium$bufferAddress = DirectBufferAddress.of(this.field_179001_a);
    }

    /**
     * One range check per attribute write reproduces the original per-store bounds
     * semantics (contiguous span ending at offset + componentCount * type size). An
     * out-of-range cursor means the active format changed without a realigning func_178965_a.
     */
    @Unique
    private int actinium$writeOffset(int componentCount) {
        int[] offsets = ((FastVertexLayout) (Object) this.field_179011_q).actinium$offsets();
        int index = this.field_181678_g;
        if (index < 0 || index >= offsets.length) {
            throw new IllegalStateException(
                "VertexBuffer element cursor " + index + " is out of sync with format " + this.field_179011_q);
        }
        int offset = this.field_178997_d * this.field_179011_q.getNextOffset() + offsets[index];
        int end = offset + componentCount * this.field_181677_f.getType().getSize();
        int limit = this.field_179001_a.limit();
        if (end > limit) {
            throw new IndexOutOfBoundsException(
                "Vertex write [" + offset + ", " + end + ") exceeds buffer limit " + limit);
        }
        return offset;
    }

    @Inject(method = "begin", at = @At("HEAD"))
    private void actinium$begin(int glMode, VertexFormat format, CallbackInfo ci) {
        this.actinium$quadContexts.clear();
        this.actinium$activeQuadContext = null;
    }

    @Inject(method = "addVertexData", at = @At("TAIL"))
    private void actinium$addVertexData(int[] vertexData, CallbackInfo ci) {
        if (this.actinium$activeQuadContext != null) {
            this.actinium$quadContexts.add(this.actinium$snapshotQuadContext());
        }
    }

    @Inject(method = "endVertex", at = @At("TAIL"))
    private void actinium$endVertex(CallbackInfo ci) {
        if (this.actinium$activeQuadContext != null && this.field_179006_k == GL11.GL_QUADS && (this.field_178997_d & 3) == 0) {
            this.actinium$quadContexts.add(this.actinium$snapshotQuadContext());
        }
    }

    @Unique
    private VanillaQuadContext actinium$snapshotQuadContext() {
        int shaderOverrideBlockId = ShaderMaterialOverrideState.getBlockId();
        return shaderOverrideBlockId >= 0
                ? this.actinium$activeQuadContext.withBlockStateId(shaderOverrideBlockId)
                : this.actinium$activeQuadContext;
    }

    /** Reproduces the original offset arithmetic and translation handling. */
    @Overwrite(remap = false)
    public VertexBuffer func_181662_b(double x, double y, double z) {
        VertexWriters.forType(this.field_181677_f.getType()).writePosition(
            this.actinium$bufferAddress + this.actinium$writeOffset(3),
            x, y, z, this.field_179004_l, this.field_179005_m, this.field_179002_n);
        this.actinium$nextVertexFormatIndex();
        return (VertexBuffer) (Object) this;
    }

    /**
     * Returns without writing or advancing the cursor when field_78939_q is set, like the
     * original; the byte-wise layout (platform-dependent byte order) lives in the writer.
     */
    @Overwrite(remap = false)
    public VertexBuffer func_181669_b(int red, int green, int blue, int alpha) {
        if (this.field_78939_q) {
            return (VertexBuffer) (Object) this;
        }
        VertexWriters.forType(this.field_181677_f.getType()).writeColor(
            this.actinium$bufferAddress + this.actinium$writeOffset(4), red, green, blue, alpha);
        this.actinium$nextVertexFormatIndex();
        return (VertexBuffer) (Object) this;
    }

    /**
     * Writes the texture coordinate element through the pre-selected raw writer,
     * preserving the original per-type component order.
     */
    @Overwrite(remap = false)
    public VertexBuffer func_187315_a(double u, double v) {
        VertexWriters.forType(this.field_181677_f.getType()).writeTexCoord(
            this.actinium$bufferAddress + this.actinium$writeOffset(2), u, v);
        this.actinium$nextVertexFormatIndex();
        return (VertexBuffer) (Object) this;
    }

    /**
     * Writes the normal element through the pre-selected raw writer. No translation is
     * applied, matching the original method.
     */
    @Overwrite(remap = false)
    public VertexBuffer func_181663_c(float x, float y, float z) {
        VertexWriters.forType(this.field_181677_f.getType()).writeNormal(
            this.actinium$bufferAddress + this.actinium$writeOffset(3), x, y, z);
        this.actinium$nextVertexFormatIndex();
        return (VertexBuffer) (Object) this;
    }

    /**
     * Writes the lightmap element through the pre-selected raw writer, preserving the
     * original per-type component order: the block light component comes first for the
     * integer-width types.
     */
    @Overwrite(remap = false)
    public VertexBuffer func_187314_a(int skyLight, int blockLight) {
        VertexWriters.forType(this.field_181677_f.getType()).writeLightmap(
            this.actinium$bufferAddress + this.actinium$writeOffset(2), skyLight, blockLight);
        this.actinium$nextVertexFormatIndex();
        return (VertexBuffer) (Object) this;
    }

    /**
     * The ring encodes the original advance (one step forward with wraparound past
     * PADDING). The original silently kept drawing through a wrong element on cursor
     * desync; the mismatch is reported instead.
     */
    @Overwrite(remap = false)
    public void func_181667_k() {
        int[] nextIndices = ((FastVertexLayout) (Object) this.field_179011_q).actinium$nextIndices();
        int index = this.field_181678_g;
        if (index < 0 || index >= nextIndices.length) {
            throw new IllegalStateException(
                "VertexBuffer element cursor " + index + " is out of sync with format " + this.field_179011_q);
        }
        int nextIndex = nextIndices[index];
        this.field_181678_g = nextIndex;
        this.field_181677_f = this.field_179011_q.getElement(nextIndex);
    }

    @Override
    public void actinium$setActiveQuadContext(@Nullable VanillaQuadContext context) {
        this.actinium$activeQuadContext = context;
    }

    @Override
    public List<VanillaQuadContext> actinium$consumeQuadContexts() {
        List<VanillaQuadContext> copy = new ArrayList<>(this.actinium$quadContexts);
        this.actinium$quadContexts.clear();
        this.actinium$activeQuadContext = null;
        return copy;
    }

    @Override
    public List<VanillaQuadContext> actinium$copyQuadContexts() {
        return new ArrayList<>(this.actinium$quadContexts);
    }

    @Override
    public void actinium$appendQuadContexts(List<VanillaQuadContext> contexts) {
        this.actinium$quadContexts.addAll(contexts);
    }

    @Override
    public boolean actinium$isDrawing() {
        return this.field_179010_r;
    }

    @Override
    public void actinium$discard() {
        this.field_179010_r = false;
        this.func_178965_a();
    }

    @Override
    public void actinium$projectiveTexCoord(float s, float t, float r, float q) {
        if (this.field_181677_f != this.field_179011_q.getElement(this.field_181678_g)) {
            throw new IllegalStateException("VertexBuffer vertex format element is out of sync");
        }
        ProjectiveTexCoordWriter.write(
            this.field_179001_a,
            this.field_179011_q,
            this.field_181678_g,
            this.field_178997_d,
            s,
            t,
            r,
            q
        );
        this.actinium$nextVertexFormatIndex();
    }

    /**
     * Packed vertices in the shared eight-int layout the batched TESR and entity draws read.
     *
     * <p>Vanilla stores vertices in the element order and stride of the active format, which is
     * usually not the shared layout. The array is therefore repacked: the attributes the format
     * carries are copied into their fixed slots and the rest stay zero until
     * {@code TessellatorStreamingDrawer.fillMissingAttributes} fills them from GL state. Copying the
     * bytes as if they were already eight-int vertices would shift every attribute.</p>
     */
    @Unique
    private int[] actinium$packedVertices = EMPTY_VERTICES;

    @Unique
    private int actinium$packedVertexCount = -1;

    @Unique
    private static final int[] EMPTY_VERTICES = new int[0];


    @Override
    public boolean isDrawing() {
        return this.field_179010_r;
    }

    @Override
    public void setDrawing(boolean drawing) {
        this.field_179010_r = drawing;
    }

    @Override
    public int getVertexCount() {
        return this.field_178997_d;
    }

    @Override
    public int[] getRawBuffer() {
        if (this.actinium$packedVertexCount != this.field_178997_d) {
            this.actinium$packVertices();
        }
        return this.actinium$packedVertices;
    }

    @Override
    public int getRawBufferIndex() {
        return this.field_178997_d * SharedVertexLayout.VERTEX_INTS;
    }

    @Override
    public int getRawBufferSize() {
        return this.actinium$packedVertices.length;
    }

    @Override
    public void setRawBufferSize(int size) {
        // The shared view is rebuilt from the vertex buffer on every draw, so its size is derived.
    }

    @Override
    public void setRawBuffer(int[] buffer) {
        // The supplied array comes from the caller's own sizing, so it is not a packed view of the
        // current vertices; the next read repacks into it.
        this.actinium$packedVertices = buffer;
        this.actinium$packedVertexCount = -1;
    }

    @Override
    public int getDrawMode() {
        return this.field_179006_k;
    }

    @Override
    public boolean hasTexture() {
        return this.actinium$hasElement(VertexFormatElement.EnumUsage.UV, 0);
    }

    @Override
    public boolean hasColor() {
        return this.actinium$hasElement(VertexFormatElement.EnumUsage.COLOR, 0);
    }

    @Override
    public boolean hasNormals() {
        return this.actinium$hasElement(VertexFormatElement.EnumUsage.NORMAL, 0);
    }

    @Override
    public boolean hasBrightness() {
        return this.actinium$hasElement(VertexFormatElement.EnumUsage.UV, 1);
    }

    @Override
    public void angelica$reset() {
        this.func_178965_a();
        this.actinium$packedVertices = EMPTY_VERTICES;
        this.actinium$packedVertexCount = -1;
    }

    @Unique
    private boolean actinium$hasElement(VertexFormatElement.EnumUsage usage, int index) {
        return SharedVertexLayout.has(this.field_179011_q, usage, index);
    }

    @Unique
    private void actinium$packVertices() {
        final int count = this.field_178997_d;
        if (count <= 0) {
            this.actinium$packedVertices = EMPTY_VERTICES;
            this.actinium$packedVertexCount = count;
            return;
        }
        final VertexFormat format = this.field_179011_q;
        if (format == null) {
            throw new IllegalStateException("Packing " + count + " vertices without a vertex format");
        }

        final int[] packed = new int[count * SharedVertexLayout.VERTEX_INTS];
        SharedVertexLayout.pack(this.field_179001_a, format, count, packed);
        this.actinium$packedVertices = packed;
        this.actinium$packedVertexCount = count;
    }
}
