package com.unbound.core.enchant;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.unbound.core.config.DefaultConfig;
import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.effects.BreachEffect;
import com.unbound.core.enchant.effects.ChannelingEffect;
import com.unbound.core.enchant.effects.EfficiencyEffect;
import com.unbound.core.enchant.effects.FortuneEffect;
import com.unbound.core.enchant.effects.GenericEffects;
import com.unbound.core.enchant.effects.ImpalingEffect;
import com.unbound.core.enchant.effects.InfinityEffect;
import com.unbound.core.enchant.effects.IgniteEffect;
import com.unbound.core.enchant.effects.LootingEffect;
import com.unbound.core.enchant.effects.LuckEffect;
import com.unbound.core.enchant.effects.MendingEffect;
import com.unbound.core.enchant.effects.MultishotEffect;
import com.unbound.core.enchant.effects.PiercingEffect;
import com.unbound.core.enchant.effects.PowerEffect;
import com.unbound.core.enchant.effects.PunchEffect;
import com.unbound.core.enchant.effects.QuickChargeEffect;
import com.unbound.core.enchant.effects.RiptideEffect;
import com.unbound.core.enchant.effects.SharpnessEffect;
import com.unbound.core.enchant.effects.SilkTouchEffect;
import com.unbound.core.enchant.effects.SweepEffect;
import com.unbound.core.enchant.effects.ThornsEffect;
import com.unbound.core.enchant.effects.UnbreakingEffect;
import com.unbound.core.enchant.effects.VeilEffect;
import com.unbound.core.enchant.effects.WindBurstEffect;

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
        Map<String, String> map = new TreeMap<>();
        map.put("aqua-affinity", "aqua_affinity");
        map.put("blast-protection", "blast_protection");
        map.put("breach", "breach");
        map.put("channeling", "channeling");
        map.put("curse-of-binding", "binding_curse");
        map.put("curse-of-vanishing", "vanishing_curse");
        map.put("depth-strider", "depth_strider");
        map.put("efficiency", "efficiency");
        map.put("feather-falling", "feather_falling");
        map.put("fire-aspect", "fire_aspect");
        map.put("fire-protection", "fire_protection");
        map.put("flame", "flame");
        map.put("fortune", "fortune");
        map.put("frost-walker", "frost_walker");
        map.put("impaling", "impaling");
        map.put("infinity", "infinity");
        map.put("density", "density");
        map.put("knockback", "knockback");
        map.put("looting", "looting");
        map.put("luck-of-the-sea", "luck_of_the_sea");
        map.put("lure", "lure");
        map.put("loyalty", "loyalty");
        map.put("mending", "mending");
        map.put("multishot", "multishot");
        map.put("piercing", "piercing");
        map.put("power", "power");
        map.put("projectile-protection", "projectile_protection");
        map.put("protection", "protection");
        map.put("punch", "punch");
        map.put("quick-charge", "quick_charge");
        map.put("respiration", "respiration");
        map.put("riptide", "riptide");
        map.put("sharpness", "sharpness");
        map.put("silk-touch", "silk_touch");
        map.put("soul-speed", "soul_speed");
        map.put("swift-sneak", "swift_sneak");
        map.put("sweeping-edge", "sweeping_edge");
        map.put("thorns", "thorns");
        map.put("unbreaking", "unbreaking");
        map.put("wind-burst", "wind_burst");
        VANILLA_NAMES = java.util.Collections.unmodifiableMap(map);
    }

    /** All Unbound keys, in display order (visible for tests). */
    static java.util.Set<String> keys() {
        return java.util.Collections.unmodifiableSet(VANILLA_NAMES.keySet());
    }

    /**
     * (Re)registers all definitions on the engine from the current config.
     * {@code veilState} is the shared veil store used by all VeilEffects and
     * read by the VeilHandler; a reload keeps active veils.
     */
    public static void registerAll(UniversalEnchantmentEngine engine,
                                   Supplier<UnboundConfig> config,
                                   Plugin plugin,
                                   NamespacedKey efficiencyModifierKey,
                                   com.unbound.core.enchant.VeilState veilState,
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
            EnchantmentDefinition definition = build(key, handle, toggle, config, plugin, efficiencyModifierKey, veilState);
            engine.register(definition);
        }
    }

    static EnchantmentDefinition build(String key, Enchantment handle,
                                       UnboundConfig.EnchToggle toggle,
                                       Supplier<UnboundConfig> config,
                                       Plugin plugin,
                                       NamespacedKey efficiencyModifierKey,
                                       com.unbound.core.enchant.VeilState veilState) {
        EnchantmentDefinition.Builder builder = EnchantmentDefinition.builder(key, handle)
                .enabled(toggle.enabled())
                .maxLevel(toggle.maxLevel());
        switch (key) {
            case "infinity" -> builder
                    .description("Preserves consumables, thrown items, totems and cheap placed blocks; can prevent durability loss.")
                    .actions(ActionType.ITEM_CONSUME, ActionType.ITEM_LAUNCH, ActionType.DURABILITY_DAMAGE,
                            ActionType.BLOCK_PLACE)
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

            // ---- 1.1 interpretations --------------------------------------

            case "protection" -> builder
                    .description("Veil: small all-round damage reduction for a few seconds after being hit while holding the item.")
                    .actions(ActionType.MELEE_ATTACK, ActionType.PROJECTILE_HIT, ActionType.ENTITY_DEATH)
                    .effect(new VeilEffect(config, VeilType.ALL, veilState));
            case "fire-protection" -> builder
                    .description("Veil: fire/lava damage reduction for a few seconds.")
                    .actions(ActionType.MELEE_ATTACK, ActionType.PROJECTILE_HIT, ActionType.ENTITY_DEATH)
                    .effect(new VeilEffect(config, VeilType.FIRE, veilState));
            case "blast-protection" -> builder
                    .description("Veil: explosion damage reduction for a few seconds.")
                    .actions(ActionType.MELEE_ATTACK, ActionType.PROJECTILE_HIT, ActionType.ENTITY_DEATH)
                    .effect(new VeilEffect(config, VeilType.BLAST, veilState));
            case "projectile-protection" -> builder
                    .description("Veil: projectile damage reduction for a few seconds.")
                    .actions(ActionType.MELEE_ATTACK, ActionType.PROJECTILE_HIT, ActionType.ENTITY_DEATH)
                    .actions(ActionType.PROJECTILE_IMPACT)
                    .effect(new VeilEffect(config, VeilType.PROJECTILE, veilState));
            case "feather-falling" -> builder
                    .description("Veil: fall damage reduction for a few seconds.")
                    .actions(ActionType.MELEE_ATTACK, ActionType.PROJECTILE_HIT, ActionType.ENTITY_DEATH)
                    .effect(new VeilEffect(config, VeilType.FALL, veilState));
            case "fire-aspect" -> builder
                    .description("Projectiles ignite targets; with Flame both keep targets burning until water.")
                    .actions(ActionType.PROJECTILE_HIT)
                    .effect(new IgniteEffect(config, false));
            case "flame" -> builder
                    .description("Melee hits ignite targets; with Fire Aspect both keep targets burning until water.")
                    .actions(ActionType.MELEE_IMPACT)
                    .effect(new IgniteEffect(config, true));
            case "sweeping-edge" -> builder
                    .description("Sweeping area attack on melee hits and projectile hits.")
                    .actions(ActionType.MELEE_IMPACT, ActionType.PROJECTILE_IMPACT)
                    .effect(new SweepEffect(config));
            case "thorns" -> builder
                    .description("When the wearer is hit, sweep nearby enemies (armor version of Sweeping Edge).")
                    .actions(ActionType.THORNS_TRIGGER)
                    .effect(new ThornsEffect(config));
            case "silk-touch" -> builder
                    .description("Killing a player yields their head as a bonus drop.")
                    .actions(ActionType.ENTITY_DEATH)
                    .effect(new SilkTouchEffect(config));
            case "luck-of-the-sea" -> builder
                    .description("Grants Luck while held (5s refresh), exactly while the item stays equipped.")
                    .actions(ActionType.HELD_ITEM_CHANGE, ActionType.PLAYER_JOIN, ActionType.PLAYER_QUIT)
                    .effect(new LuckEffect(config));
            case "channeling" -> builder
                    .description("Any strike during thunderstorms summons lightning on the victim.")
                    .actions(ActionType.MELEE_IMPACT, ActionType.PROJECTILE_IMPACT)
                    .effect(new ChannelingEffect(config));
            case "riptide" -> builder
                    .description("Projectiles launch with riptide speed at reduced damage; melee right-click self-launches like a trident (no water needed off-trident).")
                    .actions(ActionType.PROJECTILE_SHOOT, ActionType.RIPTIDE_LAUNCH)
                    .effect(new RiptideEffect(config));
            case "impaling" -> builder
                    .description("Strikes pierce a line of up to 5 mobs depending on level.")
                    .actions(ActionType.MELEE_IMPACT, ActionType.PROJECTILE_IMPACT)
                    .effect(new ImpalingEffect(config));
            case "breach" -> builder
                    .description("Projectile hits pierce a fraction of the victim's armor per level.")
                    .actions(ActionType.PROJECTILE_HIT)
                    .effect(new BreachEffect(config));
            case "piercing" -> builder
                    .description("Hits go through shields (disables them briefly and still deals damage).")
                    .actions(ActionType.MELEE_ATTACK, ActionType.PROJECTILE_HIT)
                    .effect(new PiercingEffect(config));
            case "wind-burst" -> builder
                    .description("Impacts burst into wind charges; on melee weapons acts like a mace smash.")
                    .actions(ActionType.MELEE_IMPACT, ActionType.PROJECTILE_IMPACT)
                    .effect(new WindBurstEffect(config));
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
            return io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.minecraft(vanillaName));
        } catch (Throwable throwable) {
            return null;
        }
    }
}
