package com.dhj.actinium.compat.chunkanimator;

import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizon.gtnhlib.compat.Mods;
import net.minecraft.util.math.BlockPos;
import dhj.embeddedt.embeddium.api.render.chunk.ChunkAnimationProvider;
import dhj.embeddedt.embeddium.api.render.chunk.ChunkAnimationProviderHolder;
import dhj.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.util.vector.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Bridges Chunk Animator (mod id {@code chunkanimator}) into the chunk pipeline.
 *
 * <p>Chunk Animator is an ASM coremod that hooks {@code RenderChunk#setOrigin} to record an
 * animation start and {@code ChunkRenderContainer#preRenderChunk} to translate while drawing.
 * Neither hook runs under this renderer, so this provider re-attaches the same two calls to the
 * section lifecycle. The section object serves as the animation identity token: the mod's handler
 * keys timestamps by identity through a {@code WeakHashMap}, so disposed sections are reclaimed
 * automatically.</p>
 *
 * <p>The mod's classes are reached through {@code MethodHandle}s rather than compiled against.
 * Chunk Animator has no 1.10.2 build, so naming its types would make this file uncompilable on the
 * version this port targets. Behaviour is unchanged: the handles are bound once, after
 * {@link #install()} has established that the mod is present, and a binding failure disables the
 * bridge with a log entry rather than taking the renderer down.</p>
 */
public final class ChunkAnimatorCompat implements ChunkAnimationProvider {

    private static final String ANIMATOR_CLASS = "lumien.chunkanimator.ChunkAnimator";
    private static final String HANDLER_CLASS = "lumien.chunkanimator.handler.AnimationHandler";

    private final Object animationHandler;
    private final MethodHandle setOrigin;
    private final MethodHandle getOffset;

    private ChunkAnimatorCompat(Object animationHandler, MethodHandle setOrigin, MethodHandle getOffset) {
        this.animationHandler = animationHandler;
        this.setOrigin = setOrigin;
        this.getOffset = getOffset;
    }

    /**
     * Installs the compatibility provider when Chunk Animator is loaded.
     *
     * <p>Must be called after the mod's pre-init has run, so its animation handler is already
     * constructed.</p>
     */
    public static void install() {
        if (!Mods.CHUNKANIMATOR) {
            return;
        }

        try {
            Class<?> animatorClass = Class.forName(ANIMATOR_CLASS);
            Object instance = animatorClass.getField("INSTANCE").get(null);
            if (instance == null) {
                ActiniumRuntime.logger().error(
                        "Chunk Animator is loaded but its instance is unavailable; chunk animations will not be applied");
                return;
            }

            Object handler = animatorClass.getField("animationHandler").get(instance);
            if (handler == null) {
                ActiniumRuntime.logger().error(
                        "Chunk Animator is loaded but its animation handler is unavailable; chunk animations will not be applied");
                return;
            }

            Class<?> handlerClass = Class.forName(HANDLER_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            // setOrigin(Object section, BlockPos origin) records the animation start.
            MethodHandle setOrigin = lookup.findVirtual(handlerClass, "setOrigin",
                    MethodType.methodType(void.class, Object.class, BlockPos.class));
            // getOffset(Object section, boolean horizontal, boolean vertical) returns the offset.
            MethodHandle getOffset = lookup.findVirtual(handlerClass, "getOffset",
                    MethodType.methodType(Vector3f.class, Object.class, boolean.class, boolean.class));

            ChunkAnimationProviderHolder.setProvider(
                    new ChunkAnimatorCompat(handler, setOrigin, getOffset));
            ActiniumRuntime.logger().info("Chunk Animator compatibility layer enabled");
        } catch (ReflectiveOperationException e) {
            ActiniumRuntime.logger().error(
                    "Chunk Animator is loaded but its animation API could not be bound; chunk animations are disabled", e);
        }
    }

    @Override
    public void onSectionAdded(@NotNull RenderSection section) {
        // Mirrors RenderChunk#setOrigin: record the animation start for a section that has just
        // entered the render distance.
        try {
            this.setOrigin.invoke(this.animationHandler, section,
                    new BlockPos(section.getOriginX(), section.getOriginY(), section.getOriginZ()));
        } catch (Throwable t) {
            ActiniumRuntime.logger().warn("Chunk Animator rejected an animation start", t);
        }
    }

    @Override
    public boolean getSectionOffset(@NotNull RenderSection section, @NotNull float[] out) {
        try {
            Vector3f offset = (Vector3f) this.getOffset.invoke(this.animationHandler, section, false, false);
            if (offset == null) {
                return false;
            }
            out[0] = offset.x;
            out[1] = offset.y;
            out[2] = offset.z;
            return true;
        } catch (Throwable t) {
            ActiniumRuntime.logger().warn("Chunk Animator could not compute a section offset", t);
            return false;
        }
    }
}
