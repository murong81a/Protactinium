package com.dhj.actinium.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;

/**
 * Missing-model detection for the terrain render pipeline.
 *
 * <p>Chunk meshes are built on worker threads where {@code getQuads} must not touch main-thread
 * GL state, but Forge's {@code FancyMissingModel} lazily renders its "missing texture" label
 * through the font renderer on first use, which requires a current GL context and throws off the
 * render thread (Extra Utilities 2 red orchid block). The vanilla missing model has no renderable
 * quads either, so both variants are skipped instead of drawn. Mods wrap baked models at bake
 * time (e.g. NeoContinuity's emissive wrapper), so wrapper chains are unwrapped before the
 * check.</p>
 */
public final class MissingModelCompat {
    private static final Logger LOGGER = LogManager.getLogger("Actinium");

    private MissingModelCompat() {
    }

    /**
     * Cached vanilla missing-model reference. Resolving it walks a four-deep getter chain through
     * the block renderer dispatcher, which runs per block render during chunk mesh building; the
     * reference itself only changes on resource reload, which clears the cache through
     * {@link #onResourceManagerReload()}. Volatile because chunk meshes are built on worker threads.
     */
    private static volatile IBakedModel cachedMissingModel;

    /** Drops the cached missing-model reference so the next check re-resolves it. */
    public static void onResourceManagerReload() {
        cachedMissingModel = null;
    }

    /**
     * Binary name of Forge's {@code FancyMissingModel.BakedModel}. The class is package-private,
     * so its name is matched instead of using {@code instanceof}; it ships with Minecraft Forge.
     */
    public static final String FANCY_MISSING_MODEL_CLASS =
            "net.minecraftforge.client.model.FancyMissingModel$BakedModel";

    /**
     * {@code BakedModelWrapper.originalModel}, read reflectively because the field is protected
     * and Forge exposes no public unwrap accessor. Used only to walk wrapper chains; nothing is
     * invoked through reflection.
     */
    private static final Field WRAPPER_ORIGINAL_MODEL;
    private static final Class<?> WRAPPER_CLASS;

    static {
        Field field = null;
        Class<?> wrapperClass = null;
        try {
            // 1.10.2 Forge has no BakedModelWrapper, so the wrapper class is located by name and
            // the field read from whatever declares it. This was already reflective; nothing
            // gains a compile-time dependency on the wrapper type.
            wrapperClass = Class.forName("net.minecraftforge.client.model.BakedModelWrapper");
            field = wrapperClass.getDeclaredField("originalModel");
            field.setAccessible(true);
        } catch (ClassNotFoundException e) {
            LOGGER.debug("BakedModelWrapper absent; model wrapper chains will not be unwrapped", e);
        } catch (NoSuchFieldException e) {
            LOGGER.debug("BakedModelWrapper.originalModel absent; wrapper chains not unwrapped", e);
        } catch (SecurityException e) {
            LOGGER.debug("Cannot read BakedModelWrapper.originalModel; wrapper chains not unwrapped", e);
        }
        WRAPPER_CLASS = wrapperClass;
        WRAPPER_ORIGINAL_MODEL = field;
    }
    /**
     * Returns true when the given baked model is a missing model (vanilla fallback or Forge's
     * fancy label variant) and must not be rendered from a chunk build worker thread.
     */
    public static boolean isMissingModel(IBakedModel model) {
        IBakedModel missingModel = cachedMissingModel;
        if (missingModel == null) {
            missingModel = Minecraft.getMinecraft().getBlockRendererDispatcher()
                    .getBlockModelShapes().getModelManager().getMissingModel();
            cachedMissingModel = missingModel;
        }
        return isMissingModel(model, missingModel);
    }

    /**
     * Pure form of {@link #isMissingModel(IBakedModel)} with the vanilla fallback model passed
     * in explicitly, so the check can be verified without a live Minecraft client.
     */
    static boolean isMissingModel(IBakedModel model, IBakedModel missingModel) {
        IBakedModel candidate = model;
        while (candidate != null) {
            if (candidate == missingModel || FANCY_MISSING_MODEL_CLASS.equals(candidate.getClass().getName())) {
                return true;
            }
            candidate = unwrap(candidate);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static IBakedModel unwrap(IBakedModel model) {
        if (WRAPPER_ORIGINAL_MODEL != null && WRAPPER_CLASS != null && WRAPPER_CLASS.isInstance(model)) {
            try {
                return (IBakedModel) WRAPPER_ORIGINAL_MODEL.get(model);
            } catch (IllegalAccessException e) {
                // Field was made accessible in the static initializer; treat as unwrapped.
            }
        }
        return null;
    }
}