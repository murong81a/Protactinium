package net.coderbot.iris.shadows.frustum.fallback;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.math.AxisAlignedBB;
import dhj.embeddedt.embeddium.impl.render.viewport.Viewport;
import dhj.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.joml.Vector3d;

public class NonCullingFrustum extends Frustum implements ViewportProvider, dhj.embeddedt.embeddium.impl.render.viewport.frustum.Frustum {
	private final Vector3d position = new Vector3d();
	private double x;
	private double y;
	private double z;

	@Override
	public void setPosition(double cameraX, double cameraY, double cameraZ) {
		super.setPosition(cameraX, cameraY, cameraZ);
		this.x = cameraX;
		this.y = cameraY;
		this.z = cameraZ;
	}

	@Override
	public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
		return true;
	}

	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return true;
	}

	/** Reports every camera-relative box as fully inside because this frustum never culls. */
	@Override
	public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return FULLY_INSIDE;
	}

	@Override
	public Viewport sodium$createViewport() {
		return new Viewport(this, position.set(x, y, z));
	}



}
