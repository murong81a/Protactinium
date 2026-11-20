package net.coderbot.iris.shadows.frustum;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.math.AxisAlignedBB;
import dhj.embeddedt.embeddium.impl.render.viewport.Viewport;
import dhj.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.joml.Vector3d;

public class CullEverythingFrustum extends Frustum implements ViewportProvider, dhj.embeddedt.embeddium.impl.render.viewport.frustum.Frustum {
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
		return false;
	}

	@Override
	public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return false;
	}

	/** Reports every camera-relative box as outside for region-level culling. */
	@Override
	public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		return OUTSIDE;
	}

	@Override
	public Viewport sodium$createViewport() {
		return new Viewport(this, position.set(x, y, z));
	}


}
