package com.dhj.actinium.mixin.vintage.core.frustum;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.Frustum;
import dhj.embeddedt.embeddium.impl.render.viewport.Viewport;
import dhj.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.spongepowered.asm.mixin.*;
import com.dhj.actinium.render.frustum.IClippingHelper;
import com.dhj.actinium.render.terrain.CameraHelper;

@Mixin(Frustum.class)
public class MixinFrustum implements ViewportProvider {
    @Shadow(remap = false)
    @Final
    private ClippingHelper field_78552_a;

    @Shadow(remap = false)
    private double field_78550_b, field_78551_c, field_78549_d;

    @Overwrite(remap = false)
    public boolean func_78548_b(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (Double.isInfinite(minX) || Double.isInfinite(minY) || Double.isInfinite(minZ) || Double.isInfinite(maxX) || Double.isInfinite(maxY) || Double.isInfinite(maxZ)) {
            return true;
        }
        return ((IClippingHelper)field_78552_a).celeritas$getJomlFrustum().testAab((float) (minX - this.field_78550_b), (float) (minY - this.field_78551_c), (float) (minZ - this.field_78549_d), (float) (maxX - this.field_78550_b), (float) (maxY - this.field_78551_c), (float) (maxZ - this.field_78549_d));
    }

    @Override
    public Viewport sodium$createViewport() {
        var helper = (IClippingHelper) field_78552_a;
        var frustum = helper.celeritas$getJomlFrustum();
        return new Viewport(frustum::testAab, new org.joml.Vector3d(this.field_78550_b, this.field_78551_c, this.field_78549_d).add(CameraHelper.getThirdPersonOffset()), helper.celeritas$getVpMatrix());
    }
}

