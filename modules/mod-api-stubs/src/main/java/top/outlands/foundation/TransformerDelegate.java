package top.outlands.foundation;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Stand-in for Outlands Foundation's explicit-transformer registry.
 *
 * <p>On Cleanroom this is the live registry the loader consults before it transforms a class. This
 * port runs on Forge 1.10.2, where no such registry exists, so the class provides the same surface
 * against a local map and nothing ever reads it.</p>
 *
 * <p>The important detail is {@link #isPresent()}: registration code must check it before calling
 * anything here, because a mod jar can be present on two loaders and only one of them will honour
 * these transforms. Existing code already performs that check by probing for this class as a
 * resource; {@code isPresent()} gives it a typed equivalent.</p>
 */
public final class TransformerDelegate {

    private static final Map<String, PriorityQueue<IExplicitTransformer>> REGISTRY = new HashMap<>();

    private TransformerDelegate() {}

    /**
     * Whether a real Outlands loader owns explicit transformers in this process.
     *
     * @return false, because this is the stand-in rather than the loader's implementation
     */
    public static boolean isPresent() {
        return false;
    }

    /** Registrations performed by this port. Nothing reads them on 1.10.2. */
    public static Map<String, PriorityQueue<IExplicitTransformer>> getExplicitTransformers() {
        return REGISTRY;
    }

    /** Records a transformer for a target. Inert on this loader. */
    public static void registerExplicitTransformer(IExplicitTransformer transformer, String target) {
        REGISTRY.computeIfAbsent(target, key -> new PriorityQueue<>()).add(transformer);
    }
}
