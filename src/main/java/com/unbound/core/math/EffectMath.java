package com.unbound.core.math;

/**
 * Pure math for the 1.1 interpretation enchantments (veils, sweeping
 * counts, impaling lines, breach, wind burst, riptide launches, ignite).
 * No Bukkit imports — unit tested.
 */
public final class EffectMath {

    private EffectMath() {
    }

    /** Veil duration in seconds for the given armor-enchantment level. */
    public static double veilSeconds(int level) {
        if (level < 1) {
            return 0.0;
        }
        return Math.min(30.0, 2.0 + 1.0 * level);
    }

    /** Number of extra sweep hits: 2 + level, capped by config elsewhere. */
    public static int sweepCount(int level, int cap) {
        if (level < 1) {
            return 0;
        }
        return Math.min(cap, 2 + level);
    }

    /** Impaling line: up to {@code min(5, level)} mobs. */
    public static int impalingTargets(int level) {
        if (level < 1) {
            return 0;
        }
        return Math.min(5, level);
    }

    /** Fraction of armor value Breach pierces through: 16% per level, capped at 80%. */
    public static double breachArmorFraction(int level) {
        if (level < 1) {
            return 0.0;
        }
        return Math.min(0.8, 0.16 * level);
    }

    /** Wind charges spawned by Wind Burst on impact: 4 + 2*level, capped. */
    public static int windBurstCharges(int level, int cap) {
        if (level < 1) {
            return 0;
        }
        return Math.min(cap, 4 + 2 * level);
    }

    /** Riptide self-launch strength: 1.0 + 0.4*level, capped at 2.8 (trident parity). */
    public static double riptideLaunchStrength(int level) {
        if (level < 1) {
            return 0.0;
        }
        return Math.min(2.8, 1.0 + 0.4 * level);
    }

    /** Fire damage/burn duration for Fire Aspect on projectiles: 4s per level, capped. */
    public static int igniteSeconds(int level, int cap) {
        if (level < 1) {
            return 0;
        }
        return Math.min(cap, 4 * level);
    }

    /** Luck effect duration: fixed 5 seconds while the item is held. */
    public static int luckSeconds() {
        return 5;
    }

    /** Fraction of the original damage dealt by a Riptide-launched projectile: 30%. */
    public static double riptideDamageFraction() {
        return 0.30;
    }
}
