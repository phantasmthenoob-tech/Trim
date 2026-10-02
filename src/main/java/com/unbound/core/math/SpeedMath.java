package com.unbound.core.math;

/**
 * Pure math for speed enchantments (Efficiency, Quick Charge).
 */
public final class SpeedMath {

    private SpeedMath() {
    }

    /**
     * Attack speed attribute bonus for Efficiency on melee weapons while held:
     * {@code perLevel * level * scaling}. Vanilla already handles faster
     * mining for effective tools, so Unbound only adds the attack portion.
     */
    public static double attackSpeedBonus(int level, double perLevel, double scaling) {
        if (level < 1) {
            return 0.0;
        }
        return Math.max(0.0, perLevel) * level * Math.max(0.0, scaling);
    }

    /**
     * Reduces an item cooldown / use time for Quick Charge.
     *
     * @param currentTicks    current cooldown in ticks
     * @param level           Quick Charge level
     * @param perLevel        fractional reduction per level
     * @param maxReduction    absolute cap on the fraction removed
     * @param minimumTicks    cooldowns at/below this are left untouched
     * @return the reduced cooldown, never above the input and never below minTicks
     */
    public static int reduceCooldown(int currentTicks, int level, double perLevel,
                                     double maxReduction, int minimumTicks) {
        if (currentTicks <= minimumTicks || level < 1 || perLevel <= 0) {
            return currentTicks;
        }
        double fraction = Math.min(Math.max(0.0, maxReduction), perLevel * level);
        int reduced = (int) Math.round(currentTicks * (1.0 - fraction));
        return Math.max(minimumTicks, Math.min(currentTicks, reduced));
    }
}
