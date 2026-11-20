package com.dhj.actinium.mixin.features.iris;

import com.dhj.actinium.config.ActiniumRuntimeOptions;
import com.dhj.actinium.compat.gibbed.ActiniumModelRenderer;
import com.dhj.actinium.mixinterface.features.iris.ModelRenderState;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.mixins.interfaces.IModelRenderer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLStateManager;

@Mixin(ModelRenderer.class)
public abstract class ModelRendererIrisMixin implements IModelRenderer, ActiniumModelRenderer, ModelRenderState {
    @Shadow(remap = false) public boolean field_78807_k;
    @Shadow(remap = false) public boolean field_78806_j;
    @Shadow(remap = false) public float field_82906_o;
    @Shadow(remap = false) public float field_82908_p;
    @Shadow(remap = false) public float field_82907_q;
    @Shadow(remap = false) public float field_78800_c;
    @Shadow(remap = false) public float field_78797_d;
    @Shadow(remap = false) public float field_78798_e;
    @Shadow(remap = false) public float field_78795_f;
    @Shadow(remap = false) public float field_78796_g;
    @Shadow(remap = false) public float field_78808_h;
    @Shadow(remap = false) public List<ModelBox> field_78804_l;
    @Shadow(remap = false) public List<ModelRenderer> field_78805_m;

    @Unique
    private int actinium$displayList;
    @Unique
    private int actinium$displayListScaleBits;

    /** Records rendered parts for geometry consumers that cannot rely on vanilla's compiled flag. */
    @Unique
    private boolean actinium$hasRenderedModel;

    @Override
    public boolean actinium$hasRenderedModel() {
        return this.actinium$hasRenderedModel;
    }

    @Inject(method = "render(F)V", at = @At("HEAD"), cancellable = true)
    private void actinium$renderWithoutDisplayList(float scale, CallbackInfo ci) {
        if (!ModelPartBatcher.isBatchingActive()
            && (IrisApi.getInstance().isShaderPackInUse() || !ActiniumRuntimeOptions.useModelRendererBatching())) {
            return;
        }

        ci.cancel();
        if (this.field_78807_k || !this.field_78806_j) {
            return;
        }

        GlStateManager.translate(this.field_82906_o, this.field_82908_p, this.field_82907_q);

        if (this.field_78795_f != 0.0F || this.field_78796_g != 0.0F || this.field_78808_h != 0.0F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.field_78800_c * scale, this.field_78797_d * scale, this.field_78798_e * scale);
            this.actinium$applyRotation();
            this.actinium$drawModel(scale);
            this.actinium$renderChildren(scale);
            GlStateManager.popMatrix();
        } else if (this.field_78800_c == 0.0F && this.field_78797_d == 0.0F && this.field_78798_e == 0.0F) {
            this.actinium$drawModel(scale);
            this.actinium$renderChildren(scale);
        } else {
            GlStateManager.translate(this.field_78800_c * scale, this.field_78797_d * scale, this.field_78798_e * scale);
            this.actinium$drawModel(scale);
            this.actinium$renderChildren(scale);
            GlStateManager.translate(-this.field_78800_c * scale, -this.field_78797_d * scale, -this.field_78798_e * scale);
        }

        GlStateManager.translate(-this.field_82906_o, -this.field_82908_p, -this.field_82907_q);
    }

    @Inject(method = "renderWithRotation(F)V", at = @At("HEAD"), cancellable = true)
    private void actinium$renderWithRotationWithoutDisplayList(float scale, CallbackInfo ci) {
        if (!ModelPartBatcher.isBatchingActive()
            && (IrisApi.getInstance().isShaderPackInUse() || !ActiniumRuntimeOptions.useModelRendererBatching())) {
            return;
        }

        ci.cancel();
        if (this.field_78807_k || !this.field_78806_j) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.field_78800_c * scale, this.field_78797_d * scale, this.field_78798_e * scale);

        if (this.field_78796_g != 0.0F) {
            GlStateManager.rotate(this.field_78796_g * (180.0F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
        }

        if (this.field_78795_f != 0.0F) {
            GlStateManager.rotate(this.field_78795_f * (180.0F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        }

        if (this.field_78808_h != 0.0F) {
            GlStateManager.rotate(this.field_78808_h * (180.0F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
        }

        this.actinium$drawModel(scale);
        GlStateManager.popMatrix();
    }

    @Override
    public void angelica$resetDisplayList() {
        this.actinium$resetDisplayList();
    }

    @Override
    public void actinium$renderGibbedSingleModelPart(float scale) {
        if (this.field_78807_k || !this.field_78806_j) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.field_78800_c * scale, this.field_78797_d * scale, this.field_78798_e * scale);

        if (this.field_78796_g != 0.0F) {
            GlStateManager.rotate(this.field_78796_g * (180.0F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
        }

        if (this.field_78795_f != 0.0F) {
            GlStateManager.rotate(this.field_78795_f * (180.0F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        }

        if (this.field_78808_h != 0.0F) {
            GlStateManager.rotate(this.field_78808_h * (180.0F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
        }

        this.actinium$drawModel(scale, false);
        GlStateManager.popMatrix();
    }

    @Override
    public void actinium$renderGibbedModel(float scale) {
        if (this.field_78807_k || !this.field_78806_j) {
            return;
        }

        GlStateManager.translate(this.field_82906_o, this.field_82908_p, this.field_82907_q);

        if (this.field_78795_f != 0.0F || this.field_78796_g != 0.0F || this.field_78808_h != 0.0F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.field_78800_c * scale, this.field_78797_d * scale, this.field_78798_e * scale);
            this.actinium$applyRotation();
            this.actinium$drawModel(scale, false);
            this.actinium$renderGibbedChildren(scale);
            GlStateManager.popMatrix();
        } else if (this.field_78800_c == 0.0F && this.field_78797_d == 0.0F && this.field_78798_e == 0.0F) {
            this.actinium$drawModel(scale, false);
            this.actinium$renderGibbedChildren(scale);
        } else {
            GlStateManager.translate(this.field_78800_c * scale, this.field_78797_d * scale, this.field_78798_e * scale);
            this.actinium$drawModel(scale, false);
            this.actinium$renderGibbedChildren(scale);
            GlStateManager.translate(-this.field_78800_c * scale, -this.field_78797_d * scale, -this.field_78798_e * scale);
        }

        GlStateManager.translate(-this.field_82906_o, -this.field_82908_p, -this.field_82907_q);
    }

    private void actinium$applyRotation() {
        if (this.field_78808_h != 0.0F) {
            GlStateManager.rotate(this.field_78808_h * (180.0F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
        }

        if (this.field_78796_g != 0.0F) {
            GlStateManager.rotate(this.field_78796_g * (180.0F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
        }

        if (this.field_78795_f != 0.0F) {
            GlStateManager.rotate(this.field_78795_f * (180.0F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        }
    }

    private void actinium$drawModel(float scale) {
        this.actinium$drawModel(scale, true);
    }

    @Unique
    private void actinium$drawModel(float scale, boolean allowDisplayLists) {
        // Empty parents must also qualify so geometry collectors can reach their rendered children.
        this.actinium$hasRenderedModel = true;
        if (this.field_78804_l.isEmpty()) {
            return;
        }

        // The batch places the part with the current modelview, exactly where the display list would
        // have drawn it; when it declines, the display list or the immediate path below draws it.
        if (ModelPartBatcher.isBatchingActive()
            && ModelPartBatcher.partDraw((ModelRenderer) (Object) this, scale, null)) {
            return;
        }

        final long drawsBefore = GLSMDraws.drawCalls;
        try {
            // Batched model formats do not carry per-vertex color, so they inherit the managed current color state.
            // Refreshing it here keeps the default color attribute and u_CurrentColor in sync with vanilla-style model draws.
            if (ActiniumRuntimeOptions.useModelRendererBatching()) {
                final var color = GLStateManager.getColor();
                if (color.getRed() < 0.0F || color.getGreen() < 0.0F || color.getBlue() < 0.0F || color.getAlpha() < 0.0F) {
                    GLSMCurrentAttribs.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                } else {
                    GLSMCurrentAttribs.glColor4f(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                }
            }

            boolean batchModelQuads = ActiniumRuntimeOptions.useModelRendererBatching();
            boolean useDisplayLists = allowDisplayLists && batchModelQuads && ActiniumRuntimeOptions.useModelRendererDisplayLists();

            if (useDisplayLists) {
                int scaleBits = Float.floatToIntBits(scale);
                if (this.actinium$displayList != 0 && this.actinium$displayListScaleBits != scaleBits) {
                    this.actinium$resetDisplayList();
                }

                if (this.actinium$displayList == 0) {
                    this.actinium$compileDisplayList(scale, scaleBits);
                }

                GLSMDraws.glCallList(this.actinium$displayList);
                return;
            } else if (allowDisplayLists) {
                this.actinium$resetDisplayList();
            }

            Tessellator tessellator = Tessellator.getInstance();
            VertexBuffer buffer = tessellator.getBuffer();

            if (batchModelQuads) {
                buffer.begin(7, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
                for (ModelBox cube : this.field_78804_l) {
                    for (TexturedQuad quad : ((ModelBoxAccessor) cube).actinium$getQuadList()) {
                        actinium$appendQuad(buffer, quad, scale);
                    }
                }
                tessellator.draw();
                return;
            }

            for (ModelBox cube : this.field_78804_l) {
                cube.render(buffer, scale);
            }
        } finally {
            BatchEligibility.onPartFallback(drawsBefore, GLSMDraws.drawCalls);
        }
    }

    @Unique
    private void actinium$compileDisplayList(float scale, int scaleBits) {
        int list = GLSMDraws.glGenLists(1);
        if (list == 0) {
            throw new IllegalStateException("glGenLists returned 0 while compiling a ModelRenderer display list");
        }

        GLSMDraws.glNewList(list, GL11.GL_COMPILE);

        try {
            Tessellator tessellator = Tessellator.getInstance();
            VertexBuffer buffer = tessellator.getBuffer();

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
            for (ModelBox cube : this.field_78804_l) {
                for (TexturedQuad quad : ((ModelBoxAccessor) cube).actinium$getQuadList()) {
                    actinium$appendQuad(buffer, quad, scale);
                }
            }
            tessellator.draw();
        } finally {
            GLSMDraws.glEndList();
        }

        this.actinium$displayList = list;
        this.actinium$displayListScaleBits = scaleBits;
    }

    @Unique
    private void actinium$resetDisplayList() {
        if (this.actinium$displayList > 0) {
            GLSMDraws.glDeleteLists(this.actinium$displayList, 1);
        }
        this.actinium$displayList = 0;
        this.actinium$displayListScaleBits = 0;
    }

    private static void actinium$appendQuad(VertexBuffer buffer, TexturedQuad quad, float scale) {
        Vec3d vec3d = quad.vertexPositions[1].vector3D.subtractReverse(quad.vertexPositions[0].vector3D);
        Vec3d vec3d1 = quad.vertexPositions[1].vector3D.subtractReverse(quad.vertexPositions[2].vector3D);
        Vec3d normal = vec3d1.crossProduct(vec3d).normalize();
        float normalX = (float) normal.xCoord;
        float normalY = (float) normal.yCoord;
        float normalZ = (float) normal.zCoord;

        if (((TexturedQuadAccessor) quad).actinium$isInvertNormal()) {
            normalX = -normalX;
            normalY = -normalY;
            normalZ = -normalZ;
        }

        for (int i = 0; i < quad.nVertices; i++) {
            PositionTextureVertex vertex = quad.vertexPositions[i];
            buffer.pos(
                    vertex.vector3D.xCoord * scale,
                    vertex.vector3D.yCoord * scale,
                    vertex.vector3D.zCoord * scale
                )
                .tex(vertex.texturePositionX, vertex.texturePositionY)
                .normal(normalX, normalY, normalZ)
                .endVertex();
        }
    }

    private void actinium$renderChildren(float scale) {
        if (this.field_78805_m == null) {
            return;
        }

        for (ModelRenderer child : this.field_78805_m) {
            child.render(scale);
        }
    }

    @Unique
    private void actinium$renderGibbedChildren(float scale) {
        if (this.field_78805_m == null) {
            return;
        }

        for (ModelRenderer child : this.field_78805_m) {
            if (child instanceof ActiniumModelRenderer) {
                ((ActiniumModelRenderer) child).actinium$renderGibbedModel(scale);
            } else {
                child.render(scale);
            }
        }
    }
}
