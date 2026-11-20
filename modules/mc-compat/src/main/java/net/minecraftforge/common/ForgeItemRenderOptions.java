package net.minecraftforge.common;

import java.lang.reflect.Field;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Presence check for the Forge item-rendering options that 1.10.2 Forge does not have.
 *
 * <p>Forge 1.12.2 added {@code ForgeModContainer.allowEmissiveItems}, a config option that lets
 * item rendering take a full-brightness path. 1.10.2's Forge has no such option and no equivalent
 * one: the 1.10.2 jar contains no symbol mentioning emissive item rendering at all, so on this host
 * the feature genuinely does not exist and the emissive path is never taken. This is the
 * "feature only exists in later versions" row of COMPAT.md, so it is resolved by a presence check
 * rather than by deleting the caller's branch.</p>
 *
 * <p>The check is reflective on purpose. If a future 1.10.2 Forge build, or a backport mod, adds
 * the field, this reports the real value instead of a guess. When the field is absent the answer is
 * false, which is the truth about this host rather than a stand-in for a value that exists.</p>
 */
public final class ForgeItemRenderOptions {

    private static final Logger LOGGER = LogManager.getLogger("Protactinium");

    private static final Field ALLOW_EMISSIVE_ITEMS = findAllowEmissiveItems();

    private ForgeItemRenderOptions() {}

    private static Field findAllowEmissiveItems() {
        try {
            final Field field = ForgeModContainer.class.getField("allowEmissiveItems");
            if (field.getType() != boolean.class) {
                LOGGER.warn("ForgeModContainer.allowEmissiveItems exists but is not a boolean; "
                    + "treating emissive item rendering as unsupported");
                return null;
            }
            return field;
        } catch (NoSuchFieldException absentOnThisHost) {
            LOGGER.debug("ForgeModContainer.allowEmissiveItems is absent on this host (1.10.2 Forge "
                + "has no emissive item render path); the emissive branch stays disabled");
            return null;
        } catch (SecurityException denied) {
            LOGGER.warn("Could not read ForgeModContainer.allowEmissiveItems; treating emissive "
                + "item rendering as unsupported", denied);
            return null;
        }
    }

    /**
     * Reports whether Forge is configured to render items on its emissive path.
     *
     * @return the option's value when this host has it, or false when the option does not exist,
     *         which is the case for 1.10.2 Forge
     */
    public static boolean allowsEmissiveItems() {
        if (ALLOW_EMISSIVE_ITEMS == null) {
            return false;
        }
        try {
            return ALLOW_EMISSIVE_ITEMS.getBoolean(null);
        } catch (IllegalAccessException inaccessible) {
            LOGGER.warn("Could not read ForgeModContainer.allowEmissiveItems; treating emissive "
                + "item rendering as unsupported", inaccessible);
            return false;
        }
    }
}
