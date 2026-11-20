package com.dhj.actinium.compat.neofontrender;

import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizon.gtnhlib.compat.Mods;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Runtime bridge to Neo Font Render when it is installed.
 *
 * <p>The mod's palette-provider interface is implemented through a {@code Proxy} rather than by
 * naming its types, because Neo Font Render has no 1.10.2 build. The proxy answers exactly the
 * members upstream's anonymous class answers, so the registered object behaves identically once the
 * mod is present; when the mod is absent this returns before any of that happens.</p>
 */
public final class NeoFontRenderCompat {

    private static final String API_CLASS = "neofontrender.api.NeoFontRenderApi";
    private static final String PROVIDER_INTERFACE = "neofontrender.api.color.TextColorPaletteProvider";

    private NeoFontRenderCompat() {
    }

    public static boolean isLoaded() {
        return Mods.NEOFONTRENDER;
    }

    /**
     * Registers this renderer as the preferred legacy text colour palette source, so the font mod
     * follows the current vanilla {@code FontRenderer} colour codes exposed by this pipeline.
     */
    public static void initialize() {
        if (!isLoaded()) {
            return;
        }

        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Class<?> providerInterface = Class.forName(PROVIDER_INTERFACE);

            Object provider = Proxy.newProxyInstance(
                    NeoFontRenderCompat.class.getClassLoader(),
                    new Class<?>[] { providerInterface },
                    new PaletteHandler());

            Method register = apiClass.getMethod("registerTextColorPaletteProvider", providerInterface);
            register.invoke(null, provider);
            ActiniumRuntime.logger().info("Neo Font Render compatibility layer enabled");
        } catch (ReflectiveOperationException e) {
            ActiniumRuntime.logger().error(
                    "Neo Font Render is loaded but its palette API could not be bound; the palette bridge is disabled", e);
        }
    }

    /**
     * Answers the palette provider's members. The identity values are constants, and the colour-code
     * method returns its input unchanged, which is what upstream's provider does: this renderer
     * reports the runtime codes rather than remapping them.
     */
    private static final class PaletteHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "id":
                    return "actinium:runtime";
                case "displayName":
                    return "Actinium";
                case "priority":
                    return 200;
                case "colorCodes":
                    return args != null && args.length > 0 ? args[0] : null;
                case "toString":
                    return "ActiniumTextColorPaletteProvider";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return args != null && args.length == 1 && proxy == args[0];
                default:
                    // A member this bridge does not model: report it rather than pretending.
                    throw new UnsupportedOperationException(
                            "Neo Font Render palette member not modelled by this port: " + method.getName());
            }
        }
    }
}
