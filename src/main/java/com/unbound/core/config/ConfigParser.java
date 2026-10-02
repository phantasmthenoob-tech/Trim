package com.unbound.core.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.unbound.core.enchant.ProcessingGuard;

/**
 * Converts a plain nested map (as produced by SnakeYAML for config.yml, or by
 * flattening Bukkit's FileConfiguration) into {@link UnboundConfig}.
 *
 * <p>Pure: no Bukkit imports. Missing keys fall back to documented defaults;
 * invalid values are ignored in favor of defaults rather than throwing, so a
 * bad edit never bricks the plugin.</p>
 */
public final class ConfigParser {

    private ConfigParser() {
    }

    public static UnboundConfig parse(Map<String, Object> root) {
        Map<String, Object> progression = section(root, "progression");
        Map<String, Object> table = section(progression, "enchanting-table");
        boolean tableEnabled = bool(table, "enabled", false);
        String denyMessage = str(table, "deny-message",
                "&cThe enchanting table is disabled on this server.");

        Map<String, Object> debug = section(root, "debug");
        boolean debugEnabled = bool(debug, "enabled", false);
        boolean debugToggle = bool(debug, "allow-player-toggle", true);
        boolean debugTiming = bool(debug, "show-timing", true);

        Map<String, Object> limitsMap = section(root, "limits");
        ProcessingGuard.Limits limits = new ProcessingGuard.Limits(
                nonNegative(limitsMap, "max-entities-affected-per-action", 32),
                nonNegative(limitsMap, "max-blocks-affected-per-action", 64),
                nonNegative(limitsMap, "max-projectiles-created-per-action", 16),
                nonNegative(limitsMap, "max-effect-chain-depth", 8));

        Map<String, Object> enchantments = section(root, "enchantments");
        Map<String, UnboundConfig.EnchToggle> toggles = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : enchantments.entrySet()) {
            Map<String, Object> node = asMap(entry.getValue());
            if (node == null) {
                continue;
            }
            toggles.put(entry.getKey().toLowerCase(Locale.ROOT), new UnboundConfig.EnchToggle(
                    bool(node, "enabled", true),
                    Math.max(1, intOf(node, "max-level", 1))));
        }

        Map<String, Object> infinityMap = section(enchantments, "infinity");
        Map<String, Object> consumables = section(infinityMap, "consumables");
        Map<String, Object> durability = section(infinityMap, "durability");
        UnboundConfig.InfinitySettings infinity = new UnboundConfig.InfinitySettings(
                bool(infinityMap, "enabled", true),
                bool(consumables, "enabled", true),
                bool(consumables, "prevent-consumption", true),
                lowerSet(stringList(consumables, "excluded-materials", List.of("milk_bucket"))),
                bool(durability, "enabled", true),
                bool(durability, "prevent-durability-loss", true));

        Map<String, Object> unbreakingMap = section(enchantments, "unbreaking");
        UnboundConfig.UnbreakingSettings unbreaking = new UnboundConfig.UnbreakingSettings(
                bool(unbreakingMap, "enabled", true),
                clamp01(doubleOf(unbreakingMap, "extra-skip-chance-per-level", 0.0)));

        Map<String, Object> mendingMap = section(enchantments, "mending");
        UnboundConfig.MendingSettings mending = new UnboundConfig.MendingSettings(
                bool(mendingMap, "enabled", true),
                Math.max(0.0, doubleOf(mendingMap, "durability-per-xp", 2.0)),
                bool(mendingMap, "scan-hotbar", true));

        Map<String, Object> efficiencyMap = section(enchantments, "efficiency");
        UnboundConfig.EfficiencySettings efficiency = new UnboundConfig.EfficiencySettings(
                bool(efficiencyMap, "enabled", true),
                Math.max(0.0, doubleOf(efficiencyMap, "scaling", 1.0)),
                Math.max(0.0, doubleOf(efficiencyMap, "attack-speed-bonus-per-level", 0.2)));

        Map<String, Object> quickChargeMap = section(enchantments, "quick-charge");
        UnboundConfig.QuickChargeSettings quickCharge = new UnboundConfig.QuickChargeSettings(
                bool(quickChargeMap, "enabled", true),
                clamp01(doubleOf(quickChargeMap, "use-time-reduction-per-level", 0.15)),
                clamp01(doubleOf(quickChargeMap, "max-cooldown-reduction", 0.8)),
                Math.max(0, intOf(quickChargeMap, "minimum-cooldown-ticks", 2)));

        Map<String, Object> multishotMap = section(enchantments, "multishot");
        Map<String, Object> sword = section(multishotMap, "sword");
        Map<String, Object> bow = section(multishotMap, "bow");
        UnboundConfig.MultishotSettings multishot = new UnboundConfig.MultishotSettings(
                bool(multishotMap, "enabled", true),
                new UnboundConfig.MultishotSwordSettings(
                        bool(sword, "enabled", true),
                        Math.max(0.0, doubleOf(sword, "base-radius", 2.5)),
                        Math.max(0.0, doubleOf(sword, "radius-per-level", 1.0)),
                        Math.max(0, intOf(sword, "max-targets", 16)),
                        clamp(doubleOf(sword, "damage-fraction", 1.0), 0.05, 1.0),
                        bool(sword, "knockback", true),
                        Math.max(0.0, doubleOf(sword, "knockback-strength", 0.4)),
                        bool(sword, "skip-own-pets", true),
                        bool(sword, "skip-armor-stands", true)),
                new UnboundConfig.MultishotBowSettings(
                        bool(bow, "enabled", true),
                        Math.max(0, intOf(bow, "max-extra-arrows-per-level", 1)),
                        Math.max(0, intOf(bow, "max-total-extra-arrows", 8)),
                        Math.max(0.0, doubleOf(bow, "spread-degrees", 10.0))));

        Map<String, Object> sharpnessMap = section(enchantments, "sharpness");
        UnboundConfig.SharpnessSettings sharpness = new UnboundConfig.SharpnessSettings(
                bool(sharpnessMap, "enabled", true),
                doubleOf(sharpnessMap, "base-bonus", 1.0),
                doubleOf(sharpnessMap, "bonus-per-level", 0.5));

        Map<String, Object> powerMap = section(enchantments, "power");
        UnboundConfig.PowerSettings power = new UnboundConfig.PowerSettings(
                bool(powerMap, "enabled", true),
                Math.max(0.0, doubleOf(powerMap, "projectile-damage-per-level", 0.5)),
                lowerSet(stringList(powerMap, "projectiles", List.of("wind_charge", "snowball", "egg"))));

        Map<String, Object> punchMap = section(enchantments, "punch");
        Map<String, Object> punchMelee = section(punchMap, "melee");
        UnboundConfig.PunchSettings punch = new UnboundConfig.PunchSettings(
                bool(punchMap, "enabled", true),
                bool(punchMelee, "enabled", true),
                Math.max(0.0, doubleOf(punchMelee, "velocity-per-level", 0.35)),
                lowerSet(stringList(punchMap, "projectiles", List.of("wind_charge", "snowball", "egg"))),
                Math.max(0.0, doubleOf(punchMap, "projectile-velocity-per-level", 0.35)));

        Map<String, Object> fortuneMap = section(enchantments, "fortune");
        UnboundConfig.FortuneSettings fortune = new UnboundConfig.FortuneSettings(
                bool(fortuneMap, "enabled", true),
                clamp01(doubleOf(fortuneMap, "extra-chance-per-level", 0.25)),
                lowerSet(stringList(fortuneMap, "extra-blocks", List.of())),
                lowerSet(stringList(fortuneMap, "extra-drops",
                        List.of("raw_iron", "raw_gold", "raw_copper", "coal", "diamond", "emerald",
                                "lapis_lazuli", "redstone", "nether_quartz", "amethyst_shard",
                                "glowstone_dust", "glow_berries", "nether_wart", "flint"))));

        Map<String, Object> lootingMap = section(enchantments, "looting");
        UnboundConfig.LootingSettings looting = new UnboundConfig.LootingSettings(
                bool(lootingMap, "enabled", true),
                clamp01(doubleOf(lootingMap, "extra-chance-per-level", 0.25)),
                bool(lootingMap, "boost-rare-drops", false));

        return new UnboundConfig(tableEnabled, denyMessage, debugEnabled, debugToggle, debugTiming,
                limits, toggles, infinity, unbreaking, mending, efficiency, quickCharge, multishot,
                sharpness, power, punch, fortune, looting);
    }

    // ---- helpers -------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String key) {
        if (parent == null) {
            return Map.of();
        }
        Object value = parent.get(key);
        Map<String, Object> map = asMap(value);
        return map == null ? Map.of() : map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return null;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on", "1" -> true;
                case "false", "no", "off", "0" -> false;
                default -> fallback;
            };
        }
        return fallback;
    }

    private static int intOf(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /** Non-negative int; invalid or negative values fall back to the default. */
    private static int nonNegative(Map<String, Object> map, String key, int fallback) {
        int value = intOf(map, key, fallback);
        return value < 0 ? fallback : value;
    }

    private static double doubleOf(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String str(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static List<String> stringList(Map<String, Object> map, String key, List<String> fallback) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (Object entry : list) {
                if (entry != null) {
                    result.add(String.valueOf(entry));
                }
            }
            return List.copyOf(result);
        }
        return fallback;
    }

    private static Set<String> lowerSet(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(value.toLowerCase(Locale.ROOT).trim());
        }
        return Set.copyOf(result);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
