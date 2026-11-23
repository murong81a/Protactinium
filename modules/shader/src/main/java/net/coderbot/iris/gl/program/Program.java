package net.coderbot.iris.gl.program;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;

public final class Program extends GlResource {
	private final ProgramUniforms uniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;

	@Nullable
	private final RetainedPrograms.Sources sources;

	Program(int program, @Nullable RetainedPrograms.Sources sources, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
		super(program);

		this.sources = sources;

		this.uniforms = uniforms;
		this.samplers = samplers;
		this.images = images;

		DepthColorStorage.registerOwnedProgram(program);
	}

	private static int managedBindProgram;

	/** Whether the given program id was bound by {@link #use()} rather than by a foreign renderer. */
	public static boolean isManagedBind(int program) {
		return program != 0 && managedBindProgram == program;
	}

	/** Set with -Dactinium.probeProgramSteps=true to attribute a GL error to the step that raised it. */
	private static final boolean PROBE_STEPS = Boolean.getBoolean("actinium.probeProgramSteps");
	private static final int PROBE_STEP_LIMIT = 12;
	private static final java.util.Map<String, Integer> probeStepCounts = new java.util.HashMap<>();

	private static void probeStep(String step, int program) {
		final int error = org.lwjgl.opengl.GL11.glGetError();
		if (error == 0) {
			return;
		}
		final int seen = probeStepCounts.merge(step, Integer.valueOf(1), Integer::sum).intValue();
		if (seen <= PROBE_STEP_LIMIT) {
			net.coderbot.iris.Iris.logger.warn("[program-step] program={} step={} error={} cachedActive={} seen={}",
				Integer.valueOf(program), step, Integer.valueOf(error),
				Integer.valueOf(GLSMPrograms.getActiveProgram()), Integer.valueOf(seen));
		}
	}

	public void use() {
		if (PROBE_STEPS) {
			probeStep("enter", getGlId());
		}
		RenderSystem.memoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
		bindManaged(getGlId());
		if (PROBE_STEPS) {
			probeStep("bind", getGlId());
		}

		uniforms.update();
		if (PROBE_STEPS) {
			probeStep("uniforms", getGlId());
		}
		samplers.update();
		if (PROBE_STEPS) {
			probeStep("samplers", getGlId());
		}
		images.update();
		if (PROBE_STEPS) {
			probeStep("images", getGlId());
		}
	}

	static void bindManaged(int program) {
		final int previousManagedBind = managedBindProgram;
		managedBindProgram = program;
		try {
			GLSMPrograms.glUseProgram(program);
		} finally {
			managedBindProgram = previousManagedBind;
		}
	}

	public static void unbind() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		GLSMPrograms.glUseProgram(0);
	}

	@Override
    public void destroyInternal() {
		DepthColorStorage.unregisterOwnedProgram(getGlId());
		if (sources != null) {
			RetainedPrograms.retain(sources, getGlId());
		} else {
			GLSMPrograms.glDeleteProgram(getGlId());
		}
	}

	/**
	 * @return the OpenGL ID of this program.
	 * @deprecated this should be encapsulated eventually
	 */
	@Deprecated
	public int getProgramId() {
		return getGlId();
	}

	public int getActiveImages() {
		return images.getActiveImages();
	}
}
