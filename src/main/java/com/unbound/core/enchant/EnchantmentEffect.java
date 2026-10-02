package com.unbound.core.enchant;

/**
 * A single universal enchantment behavior. Effects receive a fully populated
 * {@link EnchantmentContext} and mutate the world/item/event as needed.
 *
 * <p>Effects must:</p>
 * <ul>
 *   <li>be cheap to run and quickly return when irrelevant;</li>
 *   <li>consult {@link EnchantmentContext#guard()} for budgets;</li>
 *   <li>never recursively trigger their own action outside the guard
 *       (nested events are marked synthetic and skipped by handlers);</li>
 *   <li>do nothing (rather than something arbitrary) when the item/action
 *       combination has no meaningful interaction.</li>
 * </ul>
 */
public interface EnchantmentEffect {

    /**
     * Secondary, effect-specific applicability check. The engine and resolver
     * already checked enabled/max-level/action support; this decides whether
     * this effect meaningfully interacts with the current context.
     */
    default boolean applies(EnchantmentContext context) {
        return true;
    }

    /** Runs the effect. Only called when {@link #applies(EnchantmentContext)} was true. */
    void execute(EnchantmentContext context);
}
