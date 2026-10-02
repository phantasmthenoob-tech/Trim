package com.unbound.core.math;

/**
 * Pure math for durability enchantments: Unbreaking, Mending, Infinity.
 */
public final class DurabilityMath {

    private DurabilityMath() {
    }

    /**
     * Vanilla Unbreaking: damage is skipped with probability
     * {@code min(1, 0.6 + 0.04 * level)} for ANY item carrying the
     * enchantment. Provided for reference/debug; vanilla applies it itself.
     */
    public static double vanillaSkipChance(int level) {
        if (level < 1) {
            return 0.0;
        }
        return Math.min(1.0, 0.6 + 0.04 * level);
    }

    /**
     * Unbound's additional skip chance per durability damage event, stacked on
     * top of vanilla (0.0 keeps exact vanilla behavior; capped at 90% so an
     * item can never become fully indestructible from this effect alone).
     */
    public static double extraSkipChance(int level, double perLevel) {
        if (level < 1 || perLevel <= 0) {
            return 0.0;
        }
        return Math.min(0.9, perLevel * level);
    }

    /** Infinity's durability mode: deterministic precedence over Unbreaking. */
    public static boolean infinityPreventsDamage(boolean durabilityEnabled, boolean preventLossEnabled) {
        return durabilityEnabled && preventLossEnabled;
    }

    /** Result of one Mending conversion. */
    public record MendResult(int durabilityRepaired, int xpSpent) {
        public static final MendResult NONE = new MendResult(0, 0);

        public boolean didRepair() {
            return durabilityRepaired > 0;
        }
    }

    /**
     * Converts XP into durability for one damaged item.
     *
     * @param damageRemaining  durability points the item is missing
     * @param xpAvailable      XP currently available to spend
     * @param durabilityPerXp  durability points gained per XP (vanilla: 2)
     * @return the repair and the XP actually spent (never exceeds xpAvailable)
     */
    public static MendResult mend(int damageRemaining, int xpAvailable, double durabilityPerXp) {
        if (damageRemaining <= 0 || xpAvailable <= 0 || durabilityPerXp <= 0) {
            return MendResult.NONE;
        }
        int maxRepairable = (int) Math.floor(xpAvailable * durabilityPerXp);
        int repair = Math.min(damageRemaining, maxRepairable);
        if (repair <= 0) {
            return MendResult.NONE;
        }
        int xpSpent = (int) Math.ceil(repair / durabilityPerXp);
        xpSpent = Math.min(xpSpent, xpAvailable);
        // Never spend more XP than the repair was worth (prevents XP loss on
        // rounding when the item fully repairs mid-conversion).
        int effectiveRepair = Math.min(damageRemaining, (int) Math.floor(xpSpent * durabilityPerXp));
        if (effectiveRepair <= 0) {
            return MendResult.NONE;
        }
        return new MendResult(effectiveRepair, xpSpent);
    }
}
