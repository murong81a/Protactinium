package com.gtnewhorizons.angelica.glsm;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

/**
 * Proves that SSBOs, compute dispatch and image stores actually run on this context.
 *
 * <p>A capability flag says the driver advertises a feature; it says nothing about whether this
 * renderer can drive it. Packs were told these features exist, took their modern code paths, and
 * rendered a wrong image with no error anywhere. Each test below performs the real operation and
 * reads the result back from the GPU; a failure demotes the corresponding flag so packs stay on the
 * paths that work.
 */
@Lwjgl3Aware
public final class GpuSelfTest {

    /** Bit for a failure of the SSBO path. */
    public static final int SSBO = 1;

    /** Bit for a failure of the compute dispatch path. */
    public static final int COMPUTE = 2;

    /** Bit for a failure of the image store path. */
    public static final int IMAGE_STORE = 4;

    private static final Logger LOGGER = LogManager.getLogger("Celeritas");

    private static final int COUNT = 16;

    private GpuSelfTest() {
    }

    /** Runs all three tests and returns a bit mask of the failures; zero means all work. */
    public static int run() {
        int failures = 0;
        if (!testComputeAndSsbo()) {
            failures |= COMPUTE | SSBO;
        }
        if (!testImageStoreAndLoad()) {
            failures |= IMAGE_STORE;
        }
        return failures;
    }

    private static boolean testComputeAndSsbo() {
        int bufferIn = 0;
        int bufferOut = 0;
        int program = 0;
        try {
            IntBuffer input = BufferUtils.createIntBuffer(COUNT);
            for (int i = 0; i < COUNT; i++) {
                input.put(i, (i + 1) * 3);
            }

            bufferIn = GL15.glGenBuffers();
            bufferOut = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferIn);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, input, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferOut);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) COUNT * Integer.BYTES,
                    GL15.GL_DYNAMIC_DRAW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, bufferIn);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, bufferOut);

            program = buildComputeProgram(
                    "#version 430 core\n"
                            + "layout(local_size_x = 1) in;\n"
                            + "layout(std430, binding = 0) buffer In { int values[]; };\n"
                            + "layout(std430, binding = 1) buffer Out { int doubled[]; };\n"
                            + "void main() { uint i = gl_GlobalInvocationID.x; doubled[i] = values[i] * 2; }\n");
            if (program == 0) {
                return false;
            }
            GL20.glUseProgram(program);
            GL43.glDispatchCompute(COUNT, 1, 1);
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            GL20.glUseProgram(0);

            ByteBuffer result = BufferUtils.createByteBuffer(COUNT * Integer.BYTES);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, bufferOut);
            GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, result);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            IntBuffer read = result.asIntBuffer();
            for (int i = 0; i < COUNT; i++) {
                if (read.get(i) != (i + 1) * 6) {
                    log("SSBO/compute test read back " + read.get(i) + " at " + i + ", expected "
                            + (i + 1) * 6);
                    return false;
                }
            }
            log("SSBO and compute dispatch verified: " + COUNT + " values doubled on the GPU");
            return true;
        } catch (Throwable failure) {
            log("SSBO/compute test could not run: " + failure);
            return false;
        } finally {
            GL20.glUseProgram(0);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, 0);
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            if (bufferIn != 0) {
                GL15.glDeleteBuffers(bufferIn);
            }
            if (bufferOut != 0) {
                GL15.glDeleteBuffers(bufferOut);
            }
        }
    }

    /**
     * Writes an R32UI image, reads it back inside the same dispatch, and copies the values out
     * through an SSBO. The readback goes through a buffer because a texture readback needs entry
     * points whose availability varies between LWJGL builds, while the buffer path was verified
     * above and cannot mask an image failure.
     */
    private static boolean testImageStoreAndLoad() {
        int texture = 0;
        int buffer = 0;
        int program = 0;
        try {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32UI, 4, 4, 0, GL30.GL_RED_INTEGER,
                    GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL42.glBindImageTexture(0, texture, 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_R32UI);

            buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) COUNT * Integer.BYTES,
                    GL15.GL_DYNAMIC_DRAW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, buffer);

            program = buildComputeProgram(
                    "#version 430 core\n"
                            + "layout(local_size_x = 4, local_size_y = 4) in;\n"
                            + "layout(r32ui, binding = 0) uniform uimage2D target;\n"
                            + "layout(std430, binding = 0) buffer Out { uint values[]; };\n"
                            + "void main() {\n"
                            + "  ivec2 p = ivec2(gl_GlobalInvocationID.xy);\n"
                            + "  imageStore(target, p, uvec4(7u));\n"
                            + "  memoryBarrier();\n"
                            + "  values[p.y * 4 + p.x] = imageLoad(target, p).r;\n"
                            + "}\n");
            if (program == 0) {
                return false;
            }
            GL20.glUseProgram(program);
            GL43.glDispatchCompute(1, 1, 1);
            GL42.glMemoryBarrier(GL42.GL_TEXTURE_UPDATE_BARRIER_BIT
                    | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            GL20.glUseProgram(0);

            ByteBuffer result = BufferUtils.createByteBuffer(COUNT * Integer.BYTES);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
            GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, result);
            IntBuffer values = result.asIntBuffer();
            for (int i = 0; i < COUNT; i++) {
                if (values.get(i) != 7) {
                    log("imageStore test read back " + values.get(i) + " at " + i + ", expected 7");
                    return false;
                }
            }
            log("image load/store verified: 16 texels written and read back on the GPU");
            return true;
        } catch (Throwable failure) {
            log("imageStore test could not run: " + failure);
            return false;
        } finally {
            GL20.glUseProgram(0);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            if (texture != 0) {
                GL11.glDeleteTextures(texture);
            }
            if (buffer != 0) {
                GL15.glDeleteBuffers(buffer);
            }
        }
    }

    private static int buildComputeProgram(String source) {
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            log("compute shader failed to compile: " + GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            return 0;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, shader);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(shader);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            log("compute program failed to link: " + GL20.glGetProgramInfoLog(program, 4096));
            GL20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private static void log(String message) {
        LOGGER.info("[GPU self test] {}", message);
    }
}
