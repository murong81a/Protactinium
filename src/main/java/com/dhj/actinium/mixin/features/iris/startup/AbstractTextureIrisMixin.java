package com.dhj.actinium.mixin.features.iris.startup;

import com.gtnewhorizons.angelica.client.rendering.TextureTracker;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public class AbstractTextureIrisMixin {
    @Shadow(remap = false)
    protected int field_110553_a;

    @Inject(
        method = "getGlTextureId()I",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/client/renderer/texture/AbstractTexture;field_110553_a:I",
            shift = At.Shift.AFTER
        )
    )
    private void actinium$trackGeneratedTexture(CallbackInfoReturnable<Integer> cir) {
        TextureTracker.INSTANCE.trackTexture(this.field_110553_a, (AbstractTexture) (Object) this);
    }
}
