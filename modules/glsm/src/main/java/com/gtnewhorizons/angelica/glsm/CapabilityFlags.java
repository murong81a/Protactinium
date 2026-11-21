package com.gtnewhorizons.angelica.glsm;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a capability flag at runtime instead of compile time.
 *
 * <p>The class this project compiles against declares every capability flag as {@code final} with a
 * constant initializer, so a field read compiles to that literal. {@code capabilities.OpenGL43} was
 * therefore always {@code false} in the built jar no matter what the live capability object held,
 * while the same flag read reflectively returned true. Every flag the renderer decides on goes
 * through this helper for that reason.
 */
public final class CapabilityFlags {

    /** Resolved fields, keyed by flag name; the resolved class is the compatibility layer's. */
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<String, Field>();

    private CapabilityFlags() {
    }

    /** Returns the named flag's live value, or false when the object or the flag is absent. */
    public static boolean of(Object capabilities, String name) {
        if (capabilities == null) {
            return false;
        }
        Field field = FIELDS.get(name);
        if (field == null) {
            try {
                field = capabilities.getClass().getField(name);
            } catch (NoSuchFieldException absent) {
                return false;
            }
            FIELDS.put(name, field);
        }
        try {
            return field.getBoolean(capabilities);
        } catch (IllegalAccessException inaccessible) {
            return false;
        }
    }
}
