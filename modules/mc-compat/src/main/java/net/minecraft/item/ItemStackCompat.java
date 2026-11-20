package net.minecraft.item;

/**
 * Back-implementations of the {@code ItemStack} surface that Minecraft gained in 1.11.
 *
 * <p>1.10.2 has no empty-stack singleton and no {@code getCount()}; both arrived in 1.11 together
 * with the {@code ItemStack.EMPTY} constant. Each helper here is the exact 1.10.2 expression for
 * the 1.11 call, so a ported call site keeps its meaning rather than being rewritten by hand.</p>
 *
 * <p>These are static helpers, not overrides: the game class cannot be extended or patched from a
 * module, and the ported sources call them directly. The policy and the reason are in COMPAT.md.</p>
 */
public final class ItemStackCompat {

    private ItemStackCompat() {}

    /**
     * The 1.11 definition of an empty stack, applied to 1.10.2 semantics.
     *
     * @param stack the stack to test, may be null
     * @return true when the stack is null or carries no item
     */
    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getItem() == null;
    }

    /**
     * The 1.10.2 spelling of the empty-stack singleton 1.11 introduced as {@code ItemStack.EMPTY}.
     *
     * <p>1.10.2 has no such constant, so the equivalent is a stack holding no item. Callers that
     * only use the stack to look up a tint or a colour accept it the same way they accept the
     * 1.11 constant, because both have no item and both fail the {@code hasTintIndex} path.</p>
     *
     * @return a stack with no item, never null
     */
    public static ItemStack empty() {
        return new ItemStack((Item) null);
    }

    /**
     * The 1.11 {@code getCount()} accessor, expressed against the 1.10.2 public field.
     *
     * <p>1.10.2 keeps the size in the public {@code stackSize} field, which is what every 1.10.2
     * call site reads. This method exists so ported code can keep the 1.11 spelling.</p>
     *
     * @param stack the stack to read, may be null
     * @return the stack size, or 0 for a null stack, matching the 1.11 empty-stack result
     */
    public static int getCount(ItemStack stack) {
        return stack == null ? 0 : stack.stackSize;
    }
}
