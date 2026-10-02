package com.unbound.core.enchant;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.unbound.core.config.DefaultConfig;
import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.effects.EfficiencyEffect;
import com.unbound.core.enchant.effects.FortuneEffect;
import com.unbound.core.enchant.effects.GenericEffects;
import com.unbound.core.enchant.effects.InfinityEffect;
import com.unbound.core.enchant.effects.LootingEffect;
import com.unbound.core.enchant.effects.MendingEffect;
import com.unbound.core.enchant.effects.MultishotEffect;
import com.unbound.core.enchant.effects.PowerEffect;
import com.unbound.core.enchant.effects.PunchEffect;
import com.unbound.core.enchant.effects.QuickChargeEffect;
import com.unbound.core.enchant.effects.SharpnessEffect;
import com.unbound.core.enchant.effects.UnbreakingEffect;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Builds and registers every {@link EnchantmentDefinition}: implemented
 * enchantments get their effect + supported actions + capability
 * requirements; the remaining vanilla enchantments are registered as honest
 * pass-through placeholders (see {@link GenericEffects}).
 *
 * <p>The mapping from Unbound key to vanilla registry name lives here and
 * nowhere else. Adding a new behavior = one entry here + one effect class.</p>
 */
public final class VanillaDefinitions {

    private VanillaDefinitions() {
    }

    /** Unbound key -> vanilla registry name. Insertion order = /unbound info order. */
    private static final Map<String, String> VANILLA_NAMES;

    static {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("infinity", "infinity");
        map.put("unbreaking", "unbreaking");
        map.put("mending", "mending");
        map.put("efficiency", "efficiency");
        map.put("quick-charge", "quick_charge");
        map.put("multishot", "multishot");
        map.put("sharpness", "sharpness");
        map.put("power", "power");
        map.put("punch", "punch");
        map.put("fortune", "fortune");
        map.put("looting", "looting");
        // Placeholders — pass-through for now.
        map.put("protection", "protection");
        map.put("fire-protection", "fire_protection");
        map.put("blast-protection", "blast_protection");
        map.put("projectile-protection", "projectile_protection");
        map.put("feather-falling", "feather_falling");
        map.put("respiration", "respiration");
        map.put("aqua-affinity", "aqua_affinity");
        map.put("thorns", "thorns");
        map.put("depth-strider", "depth_strider");
        map.put("frost-walker", "frost_walker");
        map.put("curse-of-binding", "binding_curse");
        map.put("curse-of-vanishing", "vanishing_curse");
        map.put("fire-aspect", "fire_aspect");
        map.put("knockback", "knockback");
        map.put("sweeping-edge", "sweeping_edge");
        map.put("silk-touch", "silk_touch");
        map.put("flame", "flame");
        map.put("luck-of-the-sea", "luck_of_the_sea");
        map.put("lure", "lure");
        map.put("channeling", "channeling");
        map.put("loyalty", "loyalty");
        map.put("riptide", "riptide");
        map.put("impaling", "impaling");
        map.put("soul-speed", "soul_speed");
        map.put("swift-sneak", "swift_sneak");
        map.put("breach", "breach");
        map.put("density", "density");
        map.put("wind-burst", "wind_burst");
        VANILLA_NAMES = java.util.Collections.unmodifiableMap(map);
    }

    /** All Unbound keys, in display order (visible for tests). */
    static java.util.Set<String> keys() {
        return java.util.Collections.unmodifiableSet(VANILLA_NAMES.keySet());
    }

    /**
     * (Re)registers all definitions on the engine from the current config.
     */
    public static void registerAll(UniversalEnchantmentEngine engine,
                                   Supplier<UnboundConfig> config,
                                   Plugin plugin,
                                   NamespacedKey efficiencyModifierKey,
                                   Logger logger) {
        for (Map.Entry<String, String> entry : VANILLA_NAMES.entrySet()) {
            String key = entry.getKey();
            String vanillaName = entry.getValue();
            Enchantment handle = resolve(vanillaName);
            if (handle == null) {
                logger.warning(() -> "[Unbound] Vanilla enchantment '" + vanillaName + "' not found in this"
                        + " server version; skipping its definition.");
                continue;
            }
            UnboundConfig.EnchToggle toggle = config.get().toggleFor(key);
            EnchantmentDefinition definition = build(key, handle, toggle, config, plugin, efficiencyModifierKey);
            engine.register(definition);
        }
    }

    static EnchantmentDefinition build(String key, Enchantment handle,
                                               UnboundConfig.EnchToggle toggle,
                                               Supplier<UnboundConfig> config,
                                               Plugin plugin,
                                               NamespacedKey efficiencyModifierKey) {
        EnchantmentDefinition.Builder builder = EnchantmentDefinition.builder(key, handle)
                .enabled(toggle.enabled())
                .maxLevel(toggle.maxLevel());
        switch (key) {
            case "infinity" -> builder
                    .description("Preserves consumables (food, potions, pearls...) and can prevent durability loss.")
                    .actions(ActionType.ITEM_CONSUME, ActionType.ITEM_LAUNCH, ActionType.DURABILITY_DAMAGE)
                    .effect(new InfinityEffect(config, plugin));
            case "unbreaking" -> builder
                    .description("Extra durability skip chance on top of vanilla for any durable item.")
                    .actions(ActionType.DURABILITY_DAMAGE)
                    .effect(new UnbreakingEffect(config));
            case "mending" -> builder
                    .description("Repairs any damaged durable item from collected XP.")
                    .actions(ActionType.XP_GAIN)
                    .effect(new MendingEffect(config));
            case "efficiency" -> builder
                    .description("Faster attacks on held melee weapons (mining speed stays vanilla).")
                    .actions(ActionType.HELD_ITEM_CHANGE, ActionType.PLAYER_JOIN, ActionType.PLAYER_QUIT)
                    .effect(new EfficiencyEffect(config, efficiencyModifierKey));
            case "quick-charge" -> builder
                    .description("Reduces item cooldowns; crossbows keep vanilla reload behavior.")
                    .actions(ActionType.ITEM_COOLDOWN)
                    .effect(new QuickChargeEffect(config));
            case "multishot" -> builder
                    .description("Melee hits splash to nearby targets; bows fire extra arrows.")
                    .actions(ActionType.MELEE_IMPACT, ActionType.PROJECTILE_SHOOT)
                    .effect(new MultishotEffect(config));
            case "sharpness" -> builder
                    .description("Reshaped melee damage bonus; vanilla-compatible by default.")
                    .actions(ActionType.MELEE_ATTACK)
                    .effect(new SharpnessEffect(config));
            case "power" -> builder
                    .description("Flat projectile damage for non-arrow projectiles; arrows stay vanilla.")
                    .actions(ActionType.PROJECTILE_HIT)
                    .effect(new PowerEffect(config));
            case "punch" -> builder
                    .description("Melee knockback plus knockback for non-arrow projectiles; arrows stay vanilla.")
                    .actions(ActionType.MELEE_IMPACT, ActionType.PROJECTILE_IMPACT)
                    .effect(new PunchEffect(config));
            case "fortune" -> builder
                    .description("Extra qualifying-drop bonus roll on top of vanilla Fortune.")
                    .actions(ActionType.BLOCK_BREAK)
                    .effect(new FortuneEffect(config));
            case "looting" -> builder
                    .description("Extra mob-drop bonus roll on top of vanilla Looting.")
                    .actions(ActionType.ENTITY_DEATH)
                    .effect(new LootingEffect(config));
            default -> {
                // Placeholder keys: pass-through, no actions.
                return GenericEffects.placeholder(key, handle, toggle.enabled(), toggle.maxLevel());
            }
        }
        return builder.build();
    }

    /** True when the key is one of the implemented (non-placeholder) enchantments. */
    public static boolean isImplemented(String key) {
        return DefaultConfig.IMPLEMENTED_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }

    @Nullable
    private static Enchantment resolve(String vanillaName) {
        try {
            return RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.minecraft(vanillaName));
        } catch (Throwable throwable) {
            return null;
        }
    }
}
