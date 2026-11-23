package net.coderbot.iris.gl.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.shaderpack.texture.TextureFilteringData;
import dhj.embeddedt.embeddium.impl.gl.GlObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

import org.lwjgl.BufferUtils;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;

public class GlTexture extends GlObject implements TextureAccess {
	private final TextureType target;

	public GlTexture(TextureType target, int sizeX, int sizeY, int sizeZ, int internalFormat, int format, int pixelType, byte[] pixels, TextureFilteringData filteringData) {
		this.setHandle(GLSMTextures.glGenTextures());
		bindTextureForSetup(target.getGlType(), handle());

		TextureUploadHelper.resetTextureUploadState();

		final ByteBuffer buffer = BufferUtils.createByteBuffer(pixels.length);
		buffer.put(pixels);
		buffer.flip();
		target.apply(this.handle(), sizeX, sizeY, sizeZ, internalFormat, format, pixelType, buffer);

		final int texture = this.handle();

		RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_MIN_FILTER, filteringData.shouldBlur() ? GL11.GL_LINEAR : GL11.GL_NEAREST);
		RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_MAG_FILTER, filteringData.shouldBlur() ? GL11.GL_LINEAR : GL11.GL_NEAREST);
		RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_WRAP_S, filteringData.shouldClamp() ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);

		if (sizeY > 0) {
			RenderSystem.texParameteri(texture, target.getGlType(), GL11.GL_TEXTURE_WRAP_T, filteringData.shouldClamp() ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
		}

		if (sizeZ > 0) {
			RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_WRAP_R, filteringData.shouldClamp() ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
		}

		RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_MAX_LEVEL, 0);
		RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_MIN_LOD, 0);
		RenderSystem.texParameteri(texture, target.getGlType(), GL12.GL_TEXTURE_MAX_LOD, 0);
		RenderSystem.texParameterf(texture, target.getGlType(), GL14.GL_TEXTURE_LOD_BIAS, 0.0F);

		this.target = target;

		if (target == TextureType.TEXTURE_3D) {
			probe3D(sizeX, sizeY, sizeZ);
		}

		bindTextureForSetup(target.getGlType(), 0);
	}

	private static int probed3D;

	/**
	 * One-shot readback of an uploaded 3D texture. A pack whose whole sky depends on a raw 3D LUT
	 * renders a black sky when the upload silently produced an empty texture, and no draw or state
	 * check can see that, because sampling an incomplete texture is successful and returns zero.
	 */
	private void probe3D(int sizeX, int sizeY, int sizeZ) {
		if (probed3D++ >= 4) {
			return;
		}

		bindTextureForSetup(target.getGlType(), handle());

		final int texels = sizeX * sizeY * sizeZ;
		final java.nio.ByteBuffer raw = BufferUtils.createByteBuffer(texels * 3 * Float.BYTES);
		GLSMTextures.glGetTexImage(org.lwjgl.opengl.GL12.GL_TEXTURE_3D, 0,
			org.lwjgl.opengl.GL11.GL_RGB, org.lwjgl.opengl.GL11.GL_FLOAT, raw);
		final java.nio.FloatBuffer values = raw.asFloatBuffer();

		int nonzero = 0;
		float max = 0.0F;
		for (int i = 0; i < texels * 3; i++) {
			final float v = values.get(i);
			if (v != 0.0F) {
				nonzero++;
			}
			max = Math.max(max, Math.abs(v));
		}

		final int center = (texels / 2) * 3;
		net.coderbot.iris.Iris.logger.info(
			"[custom-3d] size={}x{}x{} nonzero={}/{} max={} center=[{},{},{}]",
			Integer.valueOf(sizeX), Integer.valueOf(sizeY), Integer.valueOf(sizeZ),
			Integer.valueOf(nonzero), Integer.valueOf(texels * 3), Float.valueOf(max),
			Float.valueOf(values.get(center)), Float.valueOf(values.get(center + 1)),
			Float.valueOf(values.get(center + 2)));
	}

	public static void bindTextureForSetup(int glType, int glId) {
		GLSMTextures.glBindTexture(glType, glId);
	}

	public TextureType getTarget() {
		return target;
	}

	public void bind(int unit) {
		RenderSystem.bindTextureToUnit(target.getGlType(), unit, handle());
	}

	@Override
	public TextureType getType() {
		return target;
	}

	@Override
	public IntSupplier getTextureId() {
		return this::handle;
	}

	@Override
	protected void destroyInternal() {
		GLSMTextures.glDeleteTextures(handle());
	}
}
