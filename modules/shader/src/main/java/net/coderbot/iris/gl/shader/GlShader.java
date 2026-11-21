// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package net.coderbot.iris.gl.shader;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gl.GlResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;

import java.util.Locale;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;

/**
 * A compiled OpenGL shader object.
 */
public class GlShader extends GlResource {
    private static final Logger LOGGER = LogManager.getLogger("GlShader");

    private final String name;

    public GlShader(ShaderType type, String name, String src) {
    	super(createShader(type, name, src));

        this.name = name;
    }

    /**
     * Writes every compiled source to shaderdump/.
     *
     * <p>On by default until the transformed terrain source has been read once: the transform is
     * where the coordinate space is decided, and the pack's own file cannot show that.
     */
    private static final boolean DUMP_SOURCES = true;

    /** Keeps every distinct source, since one program name is compiled through several patches. */
    private static int dumpSequence;

    /**
     * Saves the exact source the driver compiles.
     *
     * <p>Reading the pack on disk is not the same source: the transformer rewrites vertex accesses,
     * built-in uniforms and attribute names, and a wrong rewrite is invisible in the original file.
     */
    private static void dumpSource(ShaderType type, String name, String src) {
        if (!DUMP_SOURCES) {
            return;
        }
        try {
            java.io.File dir = new java.io.File("shaderdump");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            // A sequence number, because the same name is compiled through different transform
            // paths (ATTRIBUTES and CELERITAS_TERRAIN both call their program gbuffers_terrain) and
            // a shared file name would keep only the last one written.
            String file = name.replaceAll("[^A-Za-z0-9-]", "_") + "."
                    + type.name().toLowerCase(Locale.ROOT) + "."
                    + (dumpSequence++) + ".glsl";
            try (java.io.Writer writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(new java.io.File(dir, file)), "UTF-8")) {
                writer.write(src);
            }
        } catch (java.io.IOException failure) {
            LOGGER.warn("Could not dump the shader source for " + name, failure);
        }
    }

    private static int createShader(ShaderType type, String name, String src) {
        dumpSource(type, name, src);
		int handle = GLSMPrograms.glCreateShader(type.id);
        // TODO: Iris
        // TODO: ShaderWorkaround
        GLSMPrograms.glShaderSource(handle, src + '\0');
		GLSMPrograms.glCompileShader(handle);

		GLDebug.nameObject(KHRDebug.GL_SHADER, handle, name + "(" + type.name().toLowerCase(Locale.ROOT) + ")");

		String log = RenderSystem.getShaderInfoLog(handle);

		if (!log.isEmpty()) {
			LOGGER.warn("Shader compilation log for " + name + ": " + log);
		}

		int result = GLSMPrograms.glGetShaderi(handle, GL20.GL_COMPILE_STATUS);

		if (result != GL11.GL_TRUE) {
			throw new RuntimeException("Shader compilation failed, see log for details");
		}

		return handle;
	}

    public String getName() {
        return this.name;
    }

    public int getHandle() {
    	return this.getGlId();
	}

    @Override
	protected void destroyInternal() {
        GLSMPrograms.glDeleteShader(this.getGlId());
	}
}
