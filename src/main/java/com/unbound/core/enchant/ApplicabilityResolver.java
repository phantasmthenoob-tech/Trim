package com.unbound.core.enchant;

/**
 * Decides whether a definition's effect should run for a given context.
 *
 * <p>The check is layered:</p>
 * <ol>
 *   <li>definition enabled at all (config);</li>
 *   <li>a real effect implementation exists;</li>
 *   <li>the definition supports the current action;</li>
 *   <li>the item carries the enchantment at level &gt; 0 (level already
 *       clamped to the configured maximum);</li>
 *   <li>the item satisfies the definition's capability requirements —
 *       this is the "universal, but meaningful" gate: an enchantment runs on
 *       any item that can meaningfully do the action, not only on vanilla's
 *       item classes;</li>
 *   <li>the effect's own {@link EnchantmentEffect#applies(EnchantmentContext)}
 *       judgment;</li>
 *   <li>not inside a cancelled action.</li>
 * </ol>
 *
 * <p>Pure and unit-testable: no Bukkit types beyond what the context already
 * carries.</p>
 */
public final class ApplicabilityResolver {

    public boolean shouldRun(EnchantmentContext context) {
        EnchantmentDefinition definition = context.definition().orElse(null);
        if (definition == null || !definition.enabled() || definition.effect() == null) {
            return false;
        }
        if (context.isCancelled()) {
            return false;
        }
        if (!definition.supportedActions().contains(context.action())) {
            return false;
        }
        if (context.level() <= 0 && !ActionType.LEVEL_AGNOSTIC.contains(context.action())) {
            return false;
        }
        if (!context.itemCapabilities().containsAll(definition.requiredCapabilities())) {
            return false;
        }
        return definition.effect().applies(context);
    }
}
