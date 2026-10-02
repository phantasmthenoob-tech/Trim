package com.unbound.core.enchant;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantmentDefinitionTest {

    @Test
    void builderRequiresAKey() {
        assertThrows(IllegalArgumentException.class, () -> EnchantmentDefinition.builder(null, null).build());
        assertThrows(IllegalArgumentException.class, () -> EnchantmentDefinition.builder("  ", null).build());
    }

    @Test
    void maxLevelHasAFloorOfOne() {
        assertEquals(1, EnchantmentDefinition.builder("x", null).maxLevel(0).build().maxLevel());
        assertEquals(1, EnchantmentDefinition.builder("x", null).maxLevel(-5).build().maxLevel());
        assertEquals(10, EnchantmentDefinition.builder("x", null).maxLevel(10).build().maxLevel());
    }

    @Test
    void actionSetsAreCopiedDefensively() {
        java.util.EnumSet<ActionType> mutable = java.util.EnumSet.of(ActionType.MELEE_ATTACK);
        EnchantmentDefinition definition = EnchantmentDefinition.builder("x", null)
                .actions(mutable)
                .build();
        mutable.add(ActionType.BLOCK_BREAK);
        assertFalse(definition.supportedActions().contains(ActionType.BLOCK_BREAK));
        assertThrows(UnsupportedOperationException.class, () -> definition.supportedActions().add(ActionType.XP_GAIN));
    }

    @Test
    void toBuilderPreservesEverything() {
        EnchantmentDefinition original = EnchantmentDefinition.builder("quick-charge", null)
                .enabled(false)
                .maxLevel(7)
                .actions(ActionType.ITEM_COOLDOWN)
                .description("desc")
                .effectConfig(Map.of("a", 1))
                .build();
        EnchantmentDefinition copy = original.toBuilder().build();
        assertEquals(original.key(), copy.key());
        assertFalse(copy.enabled());
        assertEquals(7, copy.maxLevel());
        assertEquals(Set.of(ActionType.ITEM_COOLDOWN), copy.supportedActions());
        assertEquals("desc", copy.description());
        assertEquals(Map.of("a", 1), copy.effectConfig());
        assertTrue(copy.effect() == null);
    }
}
