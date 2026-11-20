package top.outlands.foundation;

/**
 * Stand-in for Outlands Foundation's explicit-transformer interface.
 *
 * <p>Outlands (and the Cleanroom loader built on it) exposes a second class-transformer entry
 * point that is registered per target class, in addition to the legacy LaunchWrapper
 * {@code IClassTransformer}. That loader does not exist on Forge 1.10.2, so the type has to be
 * supplied for the ported code to compile.</p>
 *
 * <p>This declaration deliberately mirrors the upstream signature exactly, so the ported
 * registration code is unchanged. Nothing in this port ever obtains an instance of it at runtime
 * on 1.10.2: {@code OutlandsExplicitTransformerRegistration.isAvailable()} probes for
 * {@code top/outlands/foundation/TransformerDelegate.class} as a resource and the registration is
 * skipped when it is absent, which it always is here. The interface therefore exists for the
 * compiler and is inert in practice, rather than being a behaviour anyone can reach.</p>
 */
public interface IExplicitTransformer {

    /** Transforms one class whose target was fixed when the transformer was registered. */
    byte[] transform(byte[] basicClass);

    /** Priority within a target's transformer queue; higher runs earlier. */
    default int getPriority() {
        return 0;
    }
}
