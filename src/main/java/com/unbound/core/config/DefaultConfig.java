package com.unbound.core.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unbound.core.enchant.ProcessingGuard;

/**
 * Single source of truth for built-in fallback values. The shipped
 * config.yml documents these; {@link ConfigParser} falls back to them when a
 * key is missing. Kept as code (not just YAML) so defaults survive even when
 * the resource is missing or partially broken.
 */
public final class DefaultConfig {

    public static final boolean ENCHANTING_TABLE_ENABLED = false;
    public static final String ENCHANTING_DENY_MESSAGE = "&cThe enchanting table is disabled on this server.";

    public static final boolean DEBUG_ENABLED = false;
    public static final boolean DEBUG_ALLOW_PLAYER_TOGGLE = true;
    public static final boolean DEBUG_SHOW_TIMING = true;

    public static final ProcessingGuard.Limits LIMITS =
            new ProcessingGuard.Limits(32, 64, 16, 8);

    /** Enchantment keys Unbound ships behavior for, in display order. */
    public static final List<String> IMPLEMENTED_KEYS = List.of(
            "infinity", "unbreaking", "mending", "efficiency", "quick-charge",
            "multishot", "sharpness", "power", "punch", "fortune", "looting",
            // 1.1 interpretations:
            "protection", "fire-protection", "blast-protection", "projectile-protection",
            "feather-falling", "fire-aspect", "flame", "sweeping-edge", "thorns",
            "silk-touch", "luck-of-the-sea", "channeling", "riptide", "impaling",
            "breach", "piercing", "wind-burst");

    /** Enchantments registered as pass-through placeholders for now. */
    public static final List<String> PLACEHOLDER_KEYS = List.of(
            "respiration", "aqua-affinity", "depth-strider",
            "frost-walker", "curse-of-binding", "curse-of-vanishing",
            "knockback", "lure", "loyalty", "soul-speed", "swift-sneak", "density");

    private DefaultConfig() {
    }

    /** Minimal config tree equivalent to the shipped defaults (for tests/tools). */
    public static Map<String, Object> asMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("progression", Map.of("enchanting-table", Map.of(
                "enabled", ENCHANTING_TABLE_ENABLED,
                "deny-message", ENCHANTING_DENY_MESSAGE)));
        root.put("debug", Map.of(
                "enabled", DEBUG_ENABLED,
                "allow-player-toggle", DEBUG_ALLOW_PLAYER_TOGGLE,
                "show-timing", DEBUG_SHOW_TIMING));
        root.put("limits", Map.of(
                "max-entities-affected-per-action", LIMITS.maxEntities(),
                "max-blocks-affected-per-action", LIMITS.maxBlocks(),
                "max-projectiles-created-per-action", LIMITS.maxProjectiles(),
                "max-effect-chain-depth", LIMITS.maxDepth()));
        // Enchantment toggles, mirroring the shipped config.yml exactly:
        // every implemented key with its enabled state and level cap.
        Map<String, Object> enchantments = new LinkedHashMap<>();
        enchantments.put("infinity", Map.of("enabled", true, "max-level", 1,
                "restore-totems", true, "blocks", Map.of("restore", true)));
        enchantments.put("unbreaking", Map.of("enabled", true, "max-level", 10));
        enchantments.put("mending", Map.of("enabled", true, "max-level", 1));
        enchantments.put("efficiency", Map.of("enabled", true, "max-level", 10));
        enchantments.put("quick-charge", Map.of("enabled", true, "max-level", 10));
        enchantments.put("multishot", Map.of("enabled", true, "max-level", 10));
        enchantments.put("sharpness", Map.of("enabled", true, "max-level", 10));
        enchantments.put("power", Map.of("enabled", true, "max-level", 10));
        enchantments.put("punch", Map.of("enabled", true, "max-level", 10));
        enchantments.put("fortune", Map.of("enabled", true, "max-level", 10));
        enchantments.put("looting", Map.of("enabled", true, "max-level", 10));
        // 1.1 interpretation toggles (mirroring shipped config.yml):
        enchantments.put("protection", Map.of("enabled", true, "max-level", 4));
        enchantments.put("fire-protection", Map.of("enabled", true, "max-level", 4));
        enchantments.put("blast-protection", Map.of("enabled", true, "max-level", 4));
        enchantments.put("projectile-protection", Map.of("enabled", true, "max-level", 4));
        enchantments.put("feather-falling", Map.of("enabled", true, "max-level", 4));
        enchantments.put("fire-aspect", Map.of("enabled", true, "max-level", 2));
        enchantments.put("flame", Map.of("enabled", true, "max-level", 1));
        enchantments.put("sweeping-edge", Map.of("enabled", true, "max-level", 3));
        enchantments.put("thorns", Map.of("enabled", true, "max-level", 3));
        enchantments.put("silk-touch", Map.of("enabled", true, "max-level", 1));
        enchantments.put("luck-of-the-sea", Map.of("enabled", true, "max-level", 5));
        enchantments.put("channeling", Map.of("enabled", true, "max-level", 1));
        enchantments.put("riptide", Map.of("enabled", true, "max-level", 3));
        enchantments.put("impaling", Map.of("enabled", true, "max-level", 5));
        enchantments.put("breach", Map.of("enabled", true, "max-level", 4));
        enchantments.put("piercing", Map.of("enabled", true, "max-level", 4));
        enchantments.put("wind-burst", Map.of("enabled", true, "max-level", 3));
        root.put("enchantments", enchantments);
        return root;
    }
}
