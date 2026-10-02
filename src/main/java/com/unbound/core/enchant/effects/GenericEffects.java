package com.unbound.core.enchant.effects;

import java.util.Map;

import com.unbound.core.enchant.EnchantmentDefinition;
import com.unbound.core.enchant.EnchantmentEffect;

import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for <b>placeholder</b> definitions of vanilla enchantments that do
 * not yet have an Unbound interpretation (Protection, Thorns, Silk Touch,
 * Riptide, Wind Burst, ...).
 *
 * <p>They are registered so that:</p>
 * <ul>
 *   <li>/unbound info lists them honestly as pass-through;</li>
 *   <li>the enchant command can still apply them anywhere (vanilla applies
 *       their use-time behavior item-agnostically wherever it exists);</li>
 *   <li>a real effect can be slotted in later by replacing the definition —
 *       never by touching the engine.</li>
 * </ul>
 *
 * <p>No arbitrary behavior is invented for them, by design.</p>
 */
public final class GenericEffects {

    private GenericEffects() {
    }

    /**
     * Builds a pass-through definition: no supported actions, no effect.
     */
    public static EnchantmentDefinition placeholder(String key, @Nullable Enchantment handle,
                                                    boolean enabled, int maxLevel) {
        return EnchantmentDefinition.builder(key, handle)
                .enabled(enabled)
                .maxLevel(maxLevel)
                .description("Pass-through: vanilla behavior applies where possible; "
                        + "no Unbound interpretation yet.")
                .build();
    }

    /** Config node passthrough helper for future effect-backed definitions. */
    public static EnchantmentDefinition withEffect(String key, @Nullable Enchantment handle,
                                                   boolean enabled, int maxLevel,
                                                   EnchantmentEffect effect,
                                                   Map<String, Object> effectConfig,
                                                   String description) {
        return EnchantmentDefinition.builder(key, handle)
                .enabled(enabled)
                .maxLevel(maxLevel)
                .effect(effect)
                .effectConfig(effectConfig)
                .description(description)
                .build();
    }
}
