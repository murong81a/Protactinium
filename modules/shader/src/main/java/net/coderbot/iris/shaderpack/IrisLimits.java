package net.coderbot.iris.shaderpack;

public class IrisLimits {
	/**
	 * The historical colortex range, always allocated so that a pack can rely on colortex0..15 existing even
	 * when it does not mention every one of them.
	 */
	public static final int BASE_COLOR_BUFFERS = 16;

	/**
	 * The maximum number of color textures that a shader pack can write to and read from in gbuffer and composite
	 * programs.
	 *
	 * <p>Raised from 16 to 32 to match Iris 1.10.5, where colortex16..31 were added. Eclipse-Shader-SR-Support
	 * writes its motion vectors to colortex17 and samples colortex16/18, so the old limit rejected it at pipeline
	 * creation. Targets above {@link #BASE_COLOR_BUFFERS} are only allocated when the pack references them
	 * (see ProgramSet's supported-render-target computation), so packs that stay within the historical range keep
	 * their old memory use.</p>
	 */
	public static final int MAX_COLOR_BUFFERS = 32;
}
