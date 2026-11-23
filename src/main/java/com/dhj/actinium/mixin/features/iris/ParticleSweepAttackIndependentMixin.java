package com.dhj.actinium.mixin.features.iris;

import com.dhj.actinium.mixinterface.features.iris.IndependentParticleDraw;
import net.minecraft.client.particle.ParticleSweepAttack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Marks the sweep attack particle as one that opens and draws its own tessellator buffer.
 *
 * <p>Its {@code renderParticle} begins a quad run on the buffer it is handed and calls
 * {@code Tessellator.draw()} itself, so the shared particle run has to be flushed and reopened
 * around it instead of capturing it. Without this marker the capture buffer is already open when
 * the particle calls {@code begin}, and vanilla throws
 * {@code IllegalStateException: Already building!}.</p>
 */
@Mixin(ParticleSweepAttack.class)
public abstract class ParticleSweepAttackIndependentMixin implements IndependentParticleDraw {
}
