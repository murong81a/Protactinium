package com.dhj.actinium.compat.glsm;

import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import net.minecraft.client.renderer.GlStateManager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/** Creates the host callback that keeps vanilla's texture binding mirror aligned with GLSM. */
public final class VanillaTextureMirrorCompat {
    /** Runtime field aliases for MCP, renamed, SRG, and official obfuscated environments. */
    private static final String[] TEXTURE_STATE_FIELD_NAMES = {
        "textureState", "TEXTURES", "field_179174_p", "r"
    };

    /** The per-unit bound texture id, under every name this field carries across environments. */
    private static final String[] TEXTURE_NAME_FIELD_NAMES = {"textureName", "field_179059_b", "b"};

    /** Prevents instances because this class only creates the GLSM host callback. */
    private VanillaTextureMirrorCompat() {
    }

    /**
     * Resolves vanilla's texture-state array once and returns a callback for every executed
     * {@code GL_TEXTURE_2D} bind. The returned callback performs only an array lookup and accessor
     * write, keeping reflection off the binding hot path.
     */
    public static GLSMHooks.TextureBindSyncCallback createCallback() {
        final Object[] textureStates = readVanillaTextureStates();
        final MethodHandle setTextureName = resolveTextureNameSetter(textureStates);
        if (setTextureName == null) {
            // A disabled feature, not a broken renderer: the mirror only exists for third-party mods
            // that read GlStateManager reflectively, and none of them are present.
            ActiniumRuntime.logger().warn("Vanilla texture mirror disabled: the TextureState field was not found");
            return (unit, textureId) -> {};
        }
        return (unit, textureId) -> {
            if (unit < 0 || unit >= textureStates.length) {
                return;
            }
            try {
                // The handle is bound once, so the hot path is a single invoke rather than a lookup.
                setTextureName.invoke(textureStates[unit], textureId);
            } catch (Throwable failure) {
                ActiniumRuntime.logger().warn("Failed to mirror a texture binding into vanilla state", failure);
            }
        };
    }

    /**
     * Resolves a setter for the per-unit bound texture id on the state objects in the array.
     *
     * <p>This used a Mixin {@code @Accessor} until the field was found to be unreachable that way on
     * this target. The accessor's name is checked against the compile-time class, which carries MCP
     * names, so the MCP spelling is needed to compile; but the mixin is declared with
     * {@code remap = false} against the production client, where the field carries its SRG name, so
     * the MCP spelling resolves to nothing at apply time. Neither spelling works for both stages, and
     * the target class is package-private so the accessor cannot be expressed any other way. A cached
     * {@link MethodHandle} over the field's declared name sidesteps the disagreement, and the field is
     * public on {@code TextureState}, so no access override is needed.</p>
     */
    private static MethodHandle resolveTextureNameSetter(Object[] textureStates) {
        for (Object state : textureStates) {
            if (state == null) {
                continue;
            }
            for (String name : TEXTURE_NAME_FIELD_NAMES) {
                try {
                    MethodHandle setter = MethodHandles.lookup().findSetter(state.getClass(), name, int.class);
                    ActiniumRuntime.logger().info("Vanilla texture mirror bound to {}.{}", state.getClass().getName(), name);
                    return setter;
                } catch (NoSuchFieldException | IllegalAccessException expected) {
                    // Try the next spelling.
                }
            }
        }
        return null;
    }

    /**
     * Reads the package-private texture-state array once during construction. Reflection is needed
     * only to obtain the array; its elements are updated later through the configured Mixin accessor.
     */
    private static Object[] readVanillaTextureStates() {
        NoSuchFieldException missingField = null;
        for (String name : TEXTURE_STATE_FIELD_NAMES) {
            final Field texturesField;
            try {
                texturesField = GlStateManager.class.getDeclaredField(name);
            } catch (NoSuchFieldException exception) {
                if (missingField != null) {
                    exception.addSuppressed(missingField);
                }
                missingField = exception;
                ActiniumRuntime.logger().debug(
                    "Vanilla GlStateManager field '{}' was not found; trying the next mapped name",
                    name, exception);
                continue;
            }
            try {
                texturesField.setAccessible(true);
                MethodHandle getter = MethodHandles.lookup().unreflectGetter(texturesField);
                return (Object[]) getter.invoke();
            } catch (Throwable exception) {
                ActiniumRuntime.logger().error("Failed to read vanilla GlStateManager texture states", exception);
                if (exception instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Failed to read GlStateManager." + name, exception);
            }
        }
        IllegalStateException exception = new IllegalStateException(
            "Vanilla GlStateManager texture-state field is unavailable", missingField);
        ActiniumRuntime.logger().error("Failed to resolve vanilla GlStateManager texture states", exception);
        throw exception;
    }
}
