package com.unbound.core.enchant;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolver-layer checks. Contexts use a {@code null} item: unit tests run
 * without a Bukkit server, and every gate under test (enabled, effect,
 * action, level, capabilities) reads the context, not the item contents.
 */
class ApplicabilityResolverTest {

    private final ApplicabilityResolver resolver = new ApplicabilityResolver();

    @AfterEach
    void cleanUp() {
        while (ProcessingGuard.isActive()) {
            ProcessingGuard.exit();
        }
    }

    private EnchantmentContext context(ActionType action, int level,
                                       Set<ItemCapability> capabilities,
                                       EnchantmentDefinition definition) {
        ProcessingGuard guard = ProcessingGuard.enter(new ProcessingGuard.Limits(32, 64, 16, 8));
        try {
            return EnchantmentContext.builder(action, null, guard)
                    .level(level)
                    .definition(definition)
                    .itemCapabilities(capabilities)
                    .build();
        } finally {
            ProcessingGuard.exit();
        }
    }

    private EnchantmentDefinition definition(Set<ActionType> actions,
                                             Set<ItemCapability> requiredCapabilities) {
        return EnchantmentDefinition.builder("test", null)
                .enabled(true)
                .maxLevel(5)
                .actions(actions)
                .effect(context -> { })
                .requiredCapabilities(requiredCapabilities)
                .build();
    }

    @Test
    void universalApplicabilityIgnoresVanillaItemClasses() {
        // Multishot-like: any melee-capable item, regardless of being a sword.
        EnchantmentDefinition def = definition(Set.of(ActionType.MELEE_IMPACT), ItemCapability.of(ItemCapability.WEAPON_MELEE));
        assertTrue(resolver.shouldRun(context(ActionType.MELEE_IMPACT, 1, ItemCapability.of(ItemCapability.WEAPON_MELEE), def)));
        // An item without melee capability cannot meaningfully attack: do nothing.
        assertFalse(resolver.shouldRun(context(ActionType.MELEE_IMPACT, 1, Set.of(), def)));
    }

    @Test
    void disabledEnchantmentsNeverRun() {
        EnchantmentDefinition def = definition(Set.of(ActionType.MELEE_ATTACK), Set.of()).toBuilder()
                .enabled(false)
                .build();
        assertFalse(resolver.shouldRun(context(ActionType.MELEE_ATTACK, 5, ItemCapability.of(ItemCapability.WEAPON_MELEE), def)));
    }

    @Test
    void effectlessDefinitionsNeverRun() {
        EnchantmentDefinition def = EnchantmentDefinition.builder("placeholder", null)
                .enabled(true)
                .actions(ActionType.MELEE_ATTACK)
                .build();
        assertFalse(resolver.shouldRun(context(ActionType.MELEE_ATTACK, 1, Set.of(), def)));
    }

    @Test
    void unsupportedActionNeverRuns() {
        EnchantmentDefinition def = definition(Set.of(ActionType.BLOCK_BREAK), Set.of());
        assertFalse(resolver.shouldRun(context(ActionType.MELEE_ATTACK, 1, Set.of(), def)));
        assertTrue(resolver.shouldRun(context(ActionType.BLOCK_BREAK, 1, Set.of(), def)));
    }

    @Test
    void zeroLevelNeverRunsExceptLevelAgnosticActions() {
        EnchantmentDefinition def = definition(Set.of(ActionType.MELEE_ATTACK, ActionType.HELD_ITEM_CHANGE), Set.of());
        assertFalse(resolver.shouldRun(context(ActionType.MELEE_ATTACK, 0, Set.of(), def)));
        assertTrue(resolver.shouldRun(context(ActionType.HELD_ITEM_CHANGE, 0, Set.of(), def)));
    }

    @Test
    void cancelledActionsNeverRun() {
        EnchantmentDefinition def = definition(Set.of(ActionType.MELEE_ATTACK), Set.of());
        ProcessingGuard guard = ProcessingGuard.enter(new ProcessingGuard.Limits(32, 64, 16, 8));
        EnchantmentContext cancelled;
        try {
            cancelled = EnchantmentContext.builder(ActionType.MELEE_ATTACK, null, guard)
                    .level(1)
                    .definition(def)
                    .itemCapabilities(Set.of())
                    .build();
        } finally {
            ProcessingGuard.exit();
        }
        cancelled.cancelAction(); // no underlying event: only marks the context
        assertFalse(resolver.shouldRun(cancelled));
    }

    @Test
    void maxLevelClampKeepsEffectsBounded() {
        EnchantmentDefinition def = definition(Set.of(ActionType.MELEE_ATTACK), Set.of());
        assertEquals(5, UniversalEnchantmentEngine.clampLevel(100, def));
        assertEquals(5, UniversalEnchantmentEngine.clampLevel(5, def));
        assertEquals(0, UniversalEnchantmentEngine.clampLevel(0, def));
        assertEquals(0, UniversalEnchantmentEngine.clampLevel(-3, def));
    }
}
