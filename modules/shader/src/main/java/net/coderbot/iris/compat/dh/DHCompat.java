package net.coderbot.iris.compat.dh;

import com.gtnewhorizon.gtnhlib.compat.Mods;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Distant Horizons compatibility facade. DH is a 1.12.2 mod with no 1.10.2 build, so its API
 * cannot be linked against and its LOD renderer does not run here. By default DH is reported
 * absent, which makes packs take their normal paths, the only paths whose output this renderer
 * can actually produce.
 *
 * <p>Reporting DH as present was the earlier default. Measured on 2026-11-23, it made packs
 * declare the {@code dhDepthTex} samplers and take their DH branches even though no DH depth
 * texture exists: the sampler binding handed an invalid texture name to {@code glBindTextureUnit}
 * and every program use raised {@code GL_INVALID_OPERATION} (thousands per second), and packs
 * whose DH branches expect LOD buffers read nothing useful. The facade therefore defaults off;
 * {@code -Dprotactinium.iris.fakeDistantHorizons=true} restores it for a pack that refuses to
 * load without DH, and the sampler binding ignores negative texture ids so that opt-in cannot
 * flood the log again.</p>
 */
public class DHCompat {
    private static final Logger LOG = LogManager.getLogger("Iris");

    /**
     * What this facade reports to shader packs.
     *
     * <p>Absent by default. Reporting present makes a pack declare the {@code dhDepthTex} samplers
     * and take its Distant Horizons code paths, but the depth texture behind those samplers does
     * not exist, so the pack samples nothing useful and the sampler binding used to hand an
     * invalid texture name to {@code glBindTextureUnit}, raising {@code GL_INVALID_OPERATION} on
     * every program use. A pack that genuinely requires DH can opt back in with
     * {@code -Dprotactinium.iris.fakeDistantHorizons=true}.</p>
     */
    private static final boolean REPORT_PRESENT =
            "true".equalsIgnoreCase(System.getProperty("protactinium.iris.fakeDistantHorizons", "false"));

    private static final Matrix4f tempProj = new Matrix4f();

    public DHCompat(DeferredWorldRenderingPipeline pipeline, boolean renderDHShadow) {
        if (pipeline != null && REPORT_PRESENT) {
            LOG.info("Reporting Distant Horizons as present to shader packs; "
                    + "DH LOD rendering itself is not implemented on this version");
        }
    }

    public static Matrix4fc getProjection() {
        return RenderingState.INSTANCE.getProjectionMatrix();
    }

    public static void run() {
        // No DH event handlers to install; DH is never loaded.
    }

    /**
     * Whether a shader pack should believe that Distant Horizons is loaded.
     *
     * <p>This is the question packs and the pipeline's own optional paths ask. It reports true by
     * design so a pack that requires DH still loads; see the class documentation for what that
     * does and does not change. {@link #isDistantHorizonsReallyLoaded()} answers the different
     * question of whether the mod's classes exist, which is what any code touching DH's own API
     * must ask first, because on 1.10.2 those classes are not present at all.</p>
     */
    public static boolean isDistantHorizonsLoaded() {
        return REPORT_PRESENT;
    }

    /** Whether the Distant Horizons mod is actually present. Always the real mod list answer. */
    public static boolean isDistantHorizonsReallyLoaded() {
        return Mods.DISTANTHORIZONS;
    }

    /** Whether a pack should be told that DH rendering is available. */
    public static boolean isDhPipelineAvailable() {
        return REPORT_PRESENT;
    }

    public static boolean lastPackIncompatible() {
        // The facade never reports an incompatible pack: refusing here would push the pack into a
        // fallback path for a conflict that cannot occur, since no DH renderer is present.
        return false;
    }

    /**
     * Far plane the pack should assume.
     *
     * <p>DH would widen the projection to its own far plane. Since nothing is rendered out there,
     * the vanilla far plane is reported, which keeps the depth range the pack works in equal to
     * the range the terrain actually occupies.</p>
     */
    public static float getFarPlane() {
        return REPORT_PRESENT ? vanillaFarPlane() : 0.01f;
    }

    public static float getNearPlane() {
        return 0.05f;
    }

    /**
     * Render distance in blocks, as the pack should assume.
     *
     * <p>With DH the LODs would extend past the vanilla chunks. No LODs exist here, so the
     * vanilla distance is the truthful number and is also what keeps fog and shadow ranges in
     * step with the terrain that is actually drawn.</p>
     */
    public static int getRenderDistance() {
        return Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16;
    }

    public static boolean checkFrame() {
        // A DH frame is never produced, so there is never a frame to report.
        return false;
    }

    /**
     * Whether the pack should be told that DH is rendering.
     *
     * <p>This gates the {@code DISTANT_HORIZONS} GLSL macro and the {@code dh*} uniforms a pack
     * compiles against, so it has to agree with {@link #isDhPipelineAvailable()}: a pack that
     * decides at load time that DH is present and then finds the macro undefined would fail to
     * compile its own shaders. It follows {@link #REPORT_PRESENT}; false means packs see the
     * normal, non-DH pipeline this renderer can satisfy.</p>
     */
    public static boolean hasRenderingEnabled() {
        return REPORT_PRESENT;
    }

    public void clearPipeline() {
        // No DH pipeline state to clear.
    }

    /**
     * DH depth texture, as handed to a pack.
     *
     * @return -1, meaning "DH has not produced a depth texture". This is the value upstream
     *         returns before DH's first LOD frame, so packs already handle it.
     */
    public int getDepthTex() {
        return -1;
    }

    public int getDepthTexNoTranslucent() {
        return -1;
    }

    private static float vanillaFarPlane() {
        // 1.10.2 keeps the far plane on the view frustum the renderer builds each frame.
        return Math.max(64.0f, Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16.0f * 4.0f);
    }
}
