package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

public class VanillaVector3Uniform extends Uniform {
	private double cachedX;
	private double cachedY;
	private double cachedZ;
	private final Supplier<Vec3d> value;

	VanillaVector3Uniform(int location, Supplier<Vec3d> value) {
		super(location);

		this.value = value;
	}

	@Override
	public void update() {
        Vec3d newValue = value.get();

		if (newValue.xCoord != cachedX || newValue.yCoord != cachedY || newValue.zCoord != cachedZ) {
			cachedX = newValue.xCoord;
			cachedY = newValue.yCoord;
			cachedZ = newValue.zCoord;
			RenderSystem.uniform3f(location, (float) cachedX, (float) cachedY, (float) cachedZ);
		}
	}
}
