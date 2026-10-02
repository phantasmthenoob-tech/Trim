package com.unbound.core.enchant;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.unbound.core.config.ConfigTestSupport;
import com.unbound.core.config.DefaultConfig;
import com.unbound.core.config.UnboundConfig;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consistency checks between the vanilla-name map, the shipped config keys
 * and the definitions built per key. These catch the classic failure mode of
 * a config key that never matches a registered definition (silently default).
 */
class VanillaDefinitionsTest {

    private final Supplier<UnboundConfig> defaults = ConfigTestSupport::defaults;
    private final NamespacedKey modifierKey = new NamespacedKey("unbound", "efficiency_attack_speed_test");

    @Test
    void keyMapExactlyCoversImplementedPlusPlaceholderKeys() {
        Set<String> expected = new LinkedHashSet<>(DefaultConfig.IMPLEMENTED_KEYS);
        expected.addAll(DefaultConfig.PLACEHOLDER_KEYS);
        assertEquals(expected, VanillaDefinitions.keys(),
                "VanillaDefinitions must cover exactly the documented keys, in order");
        // No accidental duplicates between the two lists:
        assertEquals(DefaultConfig.IMPLEMENTED_KEYS.size() + DefaultConfig.PLACEHOLDER_KEYS.size(),
                VanillaDefinitions.keys().size());
    }

    @Test
    void implementedKeysBuildEffectBackedDefinitionsWithActions() {
        UnboundConfig config = defaults.get();
        for (String key : DefaultConfig.IMPLEMENTED_KEYS) {
            EnchantmentDefinition definition = VanillaDefinitions.build(key, null,
                    config.toggleFor(key), defaults, null, modifierKey);
            assertNotNull(definition.effect(), key + " must have an effect implementation");
            assertFalse(definition.supportedActions().isEmpty(), key + " must support actions");
            assertTrue(definition.enabled(), key + " enabled by default");
        }
    }

    @Test
    void placeholderKeysBuildPassThroughWithoutEffect() {
        UnboundConfig config = defaults.get();
        for (String key : DefaultConfig.PLACEHOLDER_KEYS) {
            EnchantmentDefinition definition = VanillaDefinitions.build(key, null,
                    config.toggleFor(key), defaults, null, modifierKey);
            assertTrue(definition.effect() == null, key + " is a pass-through: no invented effect");
            assertTrue(definition.supportedActions().isEmpty(), key + " must not dispatch");
        }
    }

    @Test
    void disabledToggleDisablesTheBuiltDefinition() {
        EnchantmentDefinition definition = VanillaDefinitions.build("sharpness", null,
                new UnboundConfig.EnchToggle(false, 3), defaults, null, modifierKey);
        assertFalse(definition.enabled());
        assertEquals(3, definition.maxLevel());
    }
}
