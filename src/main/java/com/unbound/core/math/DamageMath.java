package com.unbound.core.math;

import org.bukkit.util.Vector;

/**
 * Pure math for combat enchantments. No server state — every function takes
 * primitives (or the server-free {@link Vector}) and is unit-testable.
 */
public final class DamageMath {

    private DamageMath() {
    }

    /**
     * The damage bonus vanilla adds to ANY melee attack whose held item has
     * Sharpness (item-agnostic): {@code 0.5 + 0.5 * level}.
     */
    public static double sharpnessVanillaBonus(int level) {
        return level >= 1 ? 0.5 + 0.5 * level : 0.0;
    }

    /** Unbound's configured Sharpness bonus: {@code base + perLevel * (level - 1)}. */
    public static double sharpnessBonus(int level, double base, double perLevel) {
        return level >= 1 ? base + perLevel * (level - 1) : 0.0;
    }

    /**
     * Difference between Unbound's and vanilla's Sharpness contribution.
     * Vanilla has already embedded its own bonus into the attack damage the
     * event carries; applying only the delta reshapes Sharpness without ever
     * double counting. With the default config ({@code base=1.0,
     * perLevel=0.5}) the delta is exactly 0 and the event is not touched.
     */
    public static double sharpnessDelta(int level, double base, double perLevel) {
        return sharpnessBonus(level, base, perLevel) - sharpnessVanillaBonus(level);
    }

    /** Flat bonus for non-arrow projectiles that vanilla does not boost: {@code perLevel * level}. */
    public static double projectileDamageBonus(int level, double perLevel) {
        return level >= 1 ? perLevel * level : 0.0;
    }

    /**
     * Knockback velocity pushing a victim away from an attacker.
     * Horizontal push of {@code strength}, small vertical lift.
     */
    public static Vector punchVelocity(double fromX, double fromZ, double toX, double toZ, double strength) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) {
            return new Vector(0, Math.max(0.2, strength * 0.35), 0);
        }
        double scale = strength / length;
        return new Vector(dx * scale, Math.max(0.2, strength * 0.35), dz * scale);
    }
}
