package com.entitystudio.mobform;

import java.util.Map;

/**
 * Vanilla-tuned form stats, keyed by entity-type name so the table stays
 * Bukkit-free and headless-testable. Values are approximations of vanilla
 * Java behavior (health, damage, speed, body scale) — close enough to feel
 * like the mob, not frame-exact. A damage or speed of 0 means "leave the
 * player's own value alone".
 */
public final class FormStats {

    public record Stats(double health, double damage, double speed, double scale,
            double flySpeed, boolean fly, boolean fireImmune,
            long primaryCooldownMs, long secondaryCooldownMs,
            String primaryDesc, String secondaryDesc) {
    }

    private static final Stats FALLBACK = new Stats(20.0, 0.0, 0.0, 1.0,
            0.0, false, false, 3000L, -1L, "F: dash forward", null);

    private static final Map<String, Stats> TABLE = Map.ofEntries(
            Map.entry("EVOKER", new Stats(24.0, 0.0, 0.25, 1.0,
                    0.0, false, false, 5000L, 2000L,
                    "F: summon vexes", "Shift+F: fang line")),
            Map.entry("BLAZE", new Stats(20.0, 0.0, 0.23, 1.0,
                    0.3, true, true, 5000L, -1L,
                    "F: fireball burst", null)),
            Map.entry("SHEEP", new Stats(8.0, 0.0, 0.23, 0.7,
                    0.0, false, false, 1000L, 20000L,
                    "F: graze grass", "Shift+F: have a lamb")),
            Map.entry("CREEPER", new Stats(20.0, 0.0, 0.25, 1.0,
                    0.0, false, false, 30000L, -1L,
                    "F: explode (breaks blocks)", null)),
            Map.entry("ENDERMAN", new Stats(40.0, 7.0, 0.3, 1.6,
                    0.0, false, false, 1000L, -1L,
                    "F: teleport 16 blocks", null)),
            Map.entry("SKELETON", new Stats(20.0, 3.0, 0.25, 1.1,
                    0.0, false, false, 1200L, -1L,
                    "F: shoot arrow (uses arrows)", null)),
            Map.entry("STRAY", new Stats(20.0, 3.0, 0.25, 1.1,
                    0.0, false, false, 1200L, -1L,
                    "F: shoot arrow (slowness-tipped)", null)),
            Map.entry("PILLAGER", new Stats(24.0, 0.0, 0.35, 1.0,
                    0.0, false, false, 1500L, -1L,
                    "F: shoot arrow (uses arrows)", null)),
            Map.entry("PIGLIN", new Stats(16.0, 0.0, 0.35, 1.0,
                    0.0, false, false, 1500L, -1L,
                    "F: shoot arrow (uses arrows)", null)),
            Map.entry("PIGLIN_BRUTE", new Stats(50.0, 0.0, 0.35, 1.0,
                    0.0, false, false, 3000L, -1L,
                    "F: dash forward (golden axe)", null)),
            Map.entry("HUSK", new Stats(20.0, 3.0, 0.23, 1.08,
                    0.0, false, false, 3000L, -1L,
                    "F: dash · hits cause hunger", null)),
            Map.entry("DROWNED", new Stats(20.0, 3.0, 0.23, 1.0,
                    0.0, false, false, 2000L, -1L,
                    "F: throw trident", null)),
            Map.entry("WITHER_SKELETON", new Stats(20.0, 8.0, 0.25, 1.2,
                    0.0, false, false, 3000L, -1L,
                    "F: dash forward (stone sword)", null)),
            Map.entry("VINDICATOR", new Stats(24.0, 0.0, 0.25, 1.0,
                    0.0, false, false, 3000L, -1L,
                    "F: dash forward (iron axe)", null)),
            Map.entry("ZOMBIE", new Stats(20.0, 3.0, 0.23, 1.08,
                    0.0, false, false, 60000L, -1L,
                    "F: strength surge (10s)", null)),
            Map.entry("SPIDER", new Stats(16.0, 2.0, 0.3, 0.55,
                    0.0, false, false, 3000L, -1L,
                    "F: pounce", null)),
            Map.entry("CAVE_SPIDER", new Stats(12.0, 2.0, 0.3, 0.4,
                    0.0, false, false, 3000L, -1L,
                    "F: pounce", null)),
            Map.entry("SLIME", new Stats(16.0, 2.0, 0.23, 1.0,
                    0.0, false, false, 5000L, -1L,
                    "F: slam", null)),
            Map.entry("GHAST", new Stats(10.0, 6.0, 0.3, 2.2,
                    0.4, true, false, 2500L, -1L,
                    "F: fireball", null)),
            Map.entry("WITCH", new Stats(26.0, 0.0, 0.25, 1.0,
                    0.0, false, false, 2500L, -1L,
                    "F: throw random hex", null)),
            Map.entry("WITHER", new Stats(300.0, 8.0, 0.3, 1.9,
                    0.4, true, false, 2000L, 3000L,
                    "F: wither skull", "Shift+F: BLUE skull")),
            Map.entry("ENDER_DRAGON", new Stats(200.0, 0.0, 0.3, 2.6,
                    0.4, true, false, 2500L, -1L,
                    "F: dragon fireball", null)),
            Map.entry("PIG", new Stats(10.0, 0.0, 0.25, 0.7,
                    0.0, false, false, 3000L, -1L,
                    "F: dash forward", null)),
            Map.entry("COW", new Stats(10.0, 0.0, 0.2, 0.9,
                    0.0, false, false, 3000L, -1L,
                    "F: dash forward", null)),
            Map.entry("CHICKEN", new Stats(4.0, 0.0, 0.25, 0.45,
                    0.0, false, false, 3000L, -1L,
                    "F: dash forward", null)),
            Map.entry("WOLF", new Stats(8.0, 4.0, 0.3, 0.6,
                    0.0, false, false, 3000L, -1L,
                    "F: dash forward", null)),
            Map.entry("HORSE", new Stats(24.0, 0.0, 0.3, 0.95,
                    0.0, false, false, 3000L, -1L,
                    "F: dash forward", null)),
            Map.entry("PHANTOM", new Stats(20.0, 4.0, 0.3, 1.0,
                    0.3, true, false, 3000L, -1L,
                    "F: dash forward", null)),
            Map.entry("VEX", new Stats(14.0, 9.0, 0.3, 0.5,
                    0.3, true, false, 3000L, -1L,
                    "F: dash forward", null)));

    private FormStats() {
    }

    /** Stats for a type name, or the generic fallback for unlisted mobs. */
    public static Stats forType(String typeName) {
        if (typeName == null) {
            return FALLBACK;
        }
        return TABLE.getOrDefault(typeName.toUpperCase(), FALLBACK);
    }

    /** True when the type has a hand-written entry (not the fallback). */
    public static boolean isDetailed(String typeName) {
        return typeName != null && TABLE.containsKey(typeName.toUpperCase());
    }
}
