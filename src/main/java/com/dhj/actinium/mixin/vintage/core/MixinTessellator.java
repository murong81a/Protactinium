package com.dhj.actinium.mixin.vintage.core;

import com.gtnewhorizon.gtnhlib.client.renderer.ITessellatorInstance;
import dhj.embeddedt.embeddium.api.shader.buffer.VertexBufferExtension;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.dhj.actinium.render.TessellatorDrawRouter;
import com.gtnewhorizons.angelica.glsm.ITessellatorData;

@Mixin(Tessellator.class)
public class MixinTessellator implements ITessellatorInstance {
    // The target field is Tessellator.worldRenderer in 1.10.2, whose obfuscated name is
    // field_178183_a. The annotation processor emits no refmap entry for it, so the declaration
    // uses the obfuscated name directly: Mixin resolves a shadow by the name it is given, and this
    // is the name the production client actually carries.
    @Shadow
    @Final
    private VertexBuffer field_178183_a;

    @Unique
    private boolean celeritas$gtnhlibCompiling;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void celeritas$coreProfileDraw(CallbackInfo ci) {
        if (TessellatorDrawRouter.draw(this.field_178183_a, (ITessellatorData) this.field_178183_a)) {
            ci.cancel();
        }
    }

    @Override
    public void discard() {
        if (this.field_178183_a instanceof VertexBufferExtension extension) {
            extension.actinium$discard();
            return;
        }

        this.field_178183_a.reset();
    }

    @Override
    public boolean gtnhlib$isCompiling() {
        return this.celeritas$gtnhlibCompiling;
    }

    @Override
    public void gtnhlib$setCompiling(boolean compiling) {
        this.celeritas$gtnhlibCompiling = compiling;
    }
}

