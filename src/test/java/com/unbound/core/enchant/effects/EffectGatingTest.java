package com.unbound.core.enchant.effects;

import java.util.Set;
import java.util.function.Supplier;

import com.unbound.core.config.ConfigTestSupport;
import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ItemCapability;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Effect-level gating checks. Contexts are built with a {@code null} item:
 * no Bukkit server is running in unit tests, and the gates under test read
 * the action, capabilities and config — not the item contents.
 */
class EffectGatingTest {

    private EnchantmentContext context(ActionType action,
                                       Set<ItemCapability> capabilities) {
        ProcessingGuard guard = ProcessingGuard.enter(new ProcessingGuard.Limits(32, 64, 16, 8));
        try {
            return EnchantmentContext.builder(action, null, guard)
                    .itemCapabilities(capabilities)
                    .build();
        } finally {
            ProcessingGuard.exit();
        }
    }

    @Test
    void infinityConsumableGateFollowsConfigAndCapabilities() {
        // Defaults: consumables enabled and restored.
        Supplier<UnboundConfig> defaults = ConfigTestSupport::defaults;
        InfinityEffect effect = new InfinityEffect(defaults, null);

        assertTrue(effect.applies(context(ActionType.ITEM_CONSUME,
                ItemCapability.of(ItemCapability.CONSUMABLE))));
        assertTrue(effect.applies(context(ActionType.ITEM_LAUNCH,
                ItemCapability.of(ItemCapability.THROWABLE))));

        // Durability disabled in config still allows the consumable path.
        Supplier<UnboundConfig> durabilityOff = () -> ConfigTestSupport.configWithInfinity(
                new UnboundConfig.InfinitySettings(true, true, true, Set.of(), false, false, true, true, Set.of()));
        assertTrue(new InfinityEffect(durabilityOff, null).applies(context(ActionType.ITEM_CONSUME,
                ItemCapability.of(ItemCapability.CONSUMABLE))));

        // Nothing meaningful: no consumable/throwable capability -> no gate.
        assertFalse(new InfinityEffect(durabilityOff, null).applies(context(ActionType.ITEM_CONSUME,
                Set.of())));

        // Wrong action.
        assertFalse(new InfinityEffect(durabilityOff, null).applies(context(ActionType.MELEE_ATTACK,
                ItemCapability.of(ItemCapability.CONSUMABLE))));

        // Consumables disabled in config -> no restoration.
        Supplier<UnboundConfig> consumablesOff = () -> ConfigTestSupport.configWithInfinity(
                new UnboundConfig.InfinitySettings(true, false, false, Set.of(), true, true, true, true, Set.of()));
        assertFalse(new InfinityEffect(consumablesOff, null).applies(context(ActionType.ITEM_CONSUME,
                ItemCapability.of(ItemCapability.CONSUMABLE))));

        // prevent-consumption off -> nothing to do either.
        Supplier<UnboundConfig> noPrevent = () -> ConfigTestSupport.configWithInfinity(
                new UnboundConfig.InfinitySettings(true, true, false, Set.of(), true, true, true, true, Set.of()));
        assertFalse(new InfinityEffect(noPrevent, null).applies(context(ActionType.ITEM_LAUNCH,
                ItemCapability.of(ItemCapability.THROWABLE))));
    }

    @Test
    void infinityDurabilityGateFollowsConfig() {
        Supplier<UnboundConfig> defaults = ConfigTestSupport::defaults;
        assertTrue(new InfinityEffect(defaults, null).applies(context(ActionType.DURABILITY_DAMAGE,
                ItemCapability.of(ItemCapability.DURABLE))));

        Supplier<UnboundConfig> durabilityOff = () -> ConfigTestSupport.configWithInfinity(
                new UnboundConfig.InfinitySettings(true, true, true, Set.of(), false, true, true, true, Set.of()));
        assertFalse(new InfinityEffect(durabilityOff, null).applies(context(ActionType.DURABILITY_DAMAGE,
                ItemCapability.of(ItemCapability.DURABLE))));

        Supplier<UnboundConfig> preventOff = () -> ConfigTestSupport.configWithInfinity(
                new UnboundConfig.InfinitySettings(true, true, true, Set.of(), true, false, true, true, Set.of()));
        assertFalse(new InfinityEffect(preventOff, null).applies(context(ActionType.DURABILITY_DAMAGE,
                ItemCapability.of(ItemCapability.DURABLE))));
    }

    @Test
    void punchGateOnlyFiresOnImpactActions() {
        Supplier<UnboundConfig> defaults = ConfigTestSupport::defaults;
        PunchEffect effect = new PunchEffect(defaults);
        // Without a damage event in the context the effect backs off.
        assertFalse(effect.applies(context(ActionType.MELEE_IMPACT,
                ItemCapability.of(ItemCapability.WEAPON_MELEE))));
        assertFalse(effect.applies(context(ActionType.PROJECTILE_IMPACT,
                Set.of())));
        // Non-impact actions never trigger Punch, whatever the capabilities.
        assertFalse(effect.applies(context(ActionType.MELEE_ATTACK,
                ItemCapability.of(ItemCapability.WEAPON_MELEE))));
    }

    @Test
    void sharpnessAndPowerGatesRequireTheirDamageContexts() {
        Supplier<UnboundConfig> defaults = ConfigTestSupport::defaults;
        // Sharpness needs a MELEE_ATTACK carrying a damage event; the test
        // context has none, so it must not apply even on the right action.
        assertFalse(new SharpnessEffect(defaults).applies(context(ActionType.MELEE_ATTACK,
                ItemCapability.of(ItemCapability.WEAPON_MELEE))));
        // Power needs PROJECTILE_HIT with an enabled projectile; none here.
        assertFalse(new PowerEffect(defaults).applies(context(ActionType.PROJECTILE_HIT,
                Set.of())));
    }

    @Test
    void lootingRarityCheckUsesMaterialsOnly() {
        assertTrue(LootingEffect.isRareMaterial(Material.TOTEM_OF_UNDYING));
        assertTrue(LootingEffect.isRareMaterial(Material.NETHER_STAR));
        assertTrue(LootingEffect.isRareMaterial(Material.ELYTRA));
        assertFalse(LootingEffect.isRareMaterial(Material.ROTTEN_FLESH));
        assertFalse(LootingEffect.isRareMaterial(Material.BONE));
    }
}
