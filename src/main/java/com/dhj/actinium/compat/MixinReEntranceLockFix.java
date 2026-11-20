package com.dhj.actinium.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.ReEntranceLock;

import java.util.Iterator;
import java.util.Set;

/**
 * Works around a re-entrance lock leak in the Cleanroom sponge-mixin 0.8.7 build.
 *
 * <p>During mixin select/prepare, legacy coremod ASM transformers (e.g. Techguns') may trigger
 * nested class loads via {@code ClassWriter#getCommonSuperClass}. When the nested-loaded class
 * is a mixin target, {@code MixinProcessor.lockAndSelect}/{@code applyMixins} throw
 * {@code ReEntrantTransformerError} without popping the {@link ReEntranceLock}; the leaked depth
 * makes every later {@code applyMixins} call throw, aborting the launch with
 * {@code NoClassDefFoundError: net/minecraft/client/Minecraft}. Outside an active transform the
 * depth must be zero; {@link #clearLeakedLock()} restores that state.</p>
 */
public final class MixinReEntranceLockFix {

    private static final Logger LOGGER = LogManager.getLogger("Actinium");

    private MixinReEntranceLockFix() {
    }

    /**
     * Clears any leaked mixin re-entrance lock depth. Safe to call at any time
     * outside an active class transform; never throws.
     */
    public static void clearLeakedLock() {
        try {
            ReEntranceLock lock = MixinService.getService().getReEntranceLock();
            if (lock != null && lock.getDepth() > 0) {
                LOGGER.warn("Detected leaked mixin re-entrance lock (depth {}), draining it to keep class transforms working", lock.getDepth());
                drain(lock);
            }
        } catch (Throwable t) {
            LOGGER.warn("Mixin re-entrance lock fix unavailable; launch may fail if a legacy transformer re-enters mixin", t);
        }
    }

    /**
     * Drains the lock depth back to zero. {@link ReEntranceLock#clear()} only resets
     * the semaphore and does not touch the depth, while {@code check()} (used by
     * {@code MixinProcessor.lockAndSelect}) compares depth against maxDepth, so the
     * leaked depth has to be popped away explicitly.
     */
    static void drain(ReEntranceLock lock) {
        while (lock.getDepth() > 0) {
            lock.pop();
        }
        lock.clear();
    }

    /**
     * Removes vanilla ({@code net.minecraft.*}) entries from the class loader's
     * invalid-class negative cache. A nested class load that fails because of the
     * re-entrance described above is recorded there permanently, so the class can
     * never be loaded for real later; vanilla classes always exist, so their
     * presence in the cache can only come from such transient failures.
     */
    public static void clearInvalidVanillaClasses() {
        try {
            ClassLoader loader = MixinReEntranceLockFix.class.getClassLoader();
            if (loader.getResource("top/outlands/foundation/boot/ActualClassLoader.class") == null) {
                return;
            }
            Outlands.clearInvalidVanillaClasses(loader);
        } catch (Throwable t) {
            LOGGER.warn("Unable to clean the invalid-class cache", t);
        }
    }

    private static final class Outlands {
        private static void clearInvalidVanillaClasses(ClassLoader loader) {
            // Outlands' ActualClassLoader is not present on Forge 1.10.2, so the type is matched by
            // name and the cache reached reflectively. The caller has already established that the
            // class exists as a resource; if the reflective call does not line up, this logs and
            // leaves the cache alone rather than failing the launch.
            Class<?> actualClassLoader;
            try {
                actualClassLoader = Class.forName("top.outlands.foundation.boot.ActualClassLoader");
            } catch (ClassNotFoundException e) {
                LOGGER.debug("Outlands ActualClassLoader not present; no invalid-class cache to clean", e);
                return;
            }
            if (!actualClassLoader.isInstance(loader)) {
                return;
            }
            Set<String> invalid;
            try {
                invalid = (Set<String>) actualClassLoader.getMethod("getInvalidClasses").invoke(loader);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Outlands invalid-class cache could not be read", e);
                return;
            }
            int removed = 0;
            for (Iterator<String> it = invalid.iterator(); it.hasNext(); ) {
                if (it.next().startsWith("net.minecraft.")) {
                    it.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                LOGGER.warn("Removed {} vanilla class(es) from the invalid-class cache so they can be loaded for real", removed);
            }
        }
    }

    /**
     * Logs classes the caller loaded up front so their transformations (including mixins) are
     * complete before a nested transform can resolve them (e.g. Tessellator, whose hierarchy
     * Techguns may resolve from inside another class's transform).
     */
    public static void preloadClasses(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            if (LOGGER.isDebugEnabled()) { LOGGER.debug("Pre-loaded " + clazz.getName()); }
        }
    }
}
