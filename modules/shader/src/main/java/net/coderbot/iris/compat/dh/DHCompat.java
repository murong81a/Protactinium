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
 * Distant Horizons compatibility facade, reporting DH as present so shader packs accept the setup.
 *
 * <p>Distant Horizons (DH) is a 1.12.2 mod with no 1.10.2 build, so its API cannot be linked
 * against and its LOD renderer does not run here. Some shader packs nevertheless refuse to load
 * unless they believe DH is present and usable: they check for it, and a pack that fails that
 * check either aborts or falls back to a degraded pipeline.</p>
 *
 * <p>So this facade answers "DH is present and enabled" and then does nothing. The consequence,
 * stated plainly because it is the whole point: enabling DH-related options in a shader pack is
 * allowed and does not error, but the effect never appears, because there is no DH renderer to
 * produce it. The pack draws exactly what it would draw with those options off.</p>
 *
 * <p>Why this is safe rather than a lie that corrupts rendering: every method here returns a value
 * that a pack consumes as a hint (near plane, far plane, render distance) or an emptiness check
 * (has a depth texture). The values below are the neutral ones: a plausible far-plane pair, the
 * vanilla render distance, and -1 for "no depth texture", which is the same answer a pack gets
 * from upstream when DH itself reports that it has not rendered a frame yet. Nothing is
 * substituted for pixels.</p>
 *
 * <p>The alternative, reporting DH as absent, was tried first and is worse: the packs that require
 * DH then fail to load and the whole shader pipeline goes unused, which is a much larger visible
 * defect than one unsupported feature quietly doing nothing.</p>
 */
public class DHCompat {
    private static final Logger LOG = LogManager.getLogger("Iris");

    /**
     * What this facade reports to shader packs.
     *
     * <p>True by design, see the class documentation. Set the system property
     * {@code protactinium.iris.fakeDistantHorizons=false} to report DH as absent instead, which is
     * useful when diagnosing whether a pack's behaviour comes from this facade.</p>
     */
    private static final boolean REPORT_PRESENT =
            !"false".equalsIgnoreCase(System.getProperty("protactinium.iris.fakeDistantHorizons", "true"));

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
     * <p>This is what gates the {@code DISTANT_HORIZONS} GLSL macro and the {@code dh* } uniforms a
     * pack compiles against, so it has to agree with {@link #isDhPipelineAvailable()}: a pack that
     * decides at load time that DH is present and then finds the macro undefined will fail to
     * compile its own shaders. It reports true, and the pack's DH code paths then run against the
     * neutral values this class returns.</p>
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
