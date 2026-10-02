package com.unbound.core.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeedMathTest {

    @Test
    void attackSpeedBonusScalesWithLevelAndScaling() {
        assertEquals(0.0, SpeedMath.attackSpeedBonus(0, 0.2, 1.0));
        assertEquals(0.2, SpeedMath.attackSpeedBonus(1, 0.2, 1.0), 1e-9);
        assertEquals(2.0, SpeedMath.attackSpeedBonus(10, 0.2, 1.0), 1e-9);
        // Configured scaling multiplies the result.
        assertEquals(1.0, SpeedMath.attackSpeedBonus(5, 0.2, 1.0), 1e-9);
        assertEquals(0.5, SpeedMath.attackSpeedBonus(5, 0.2, 0.5), 1e-9);
    }

    @Test
    void quickChargeReducesCooldownsPerLevel() {
        // Ender pearl: 20 tick cooldown, 15% per level.
        assertEquals(17, SpeedMath.reduceCooldown(20, 1, 0.15, 0.8, 2));
        assertEquals(14, SpeedMath.reduceCooldown(20, 2, 0.15, 0.8, 2));
        assertEquals(5, SpeedMath.reduceCooldown(20, 5, 0.15, 0.8, 2));
    }

    @Test
    void quickChargeIsCappedByMaxReduction() {
        // Level 10 would remove 150%; capped at 80%.
        assertEquals(4, SpeedMath.reduceCooldown(20, 10, 0.15, 0.8, 2));
        // Configured cap of 50%.
        assertEquals(10, SpeedMath.reduceCooldown(20, 10, 0.15, 0.5, 2));
    }

    @Test
    void quickChargeRespectsMinimumAndNoOpCases() {
        // At/below the minimum: untouched.
        assertEquals(2, SpeedMath.reduceCooldown(2, 3, 0.15, 0.8, 2));
        assertEquals(1, SpeedMath.reduceCooldown(1, 3, 0.15, 0.8, 2));
        // Never increases a cooldown, never below the minimum.
        int reduced = SpeedMath.reduceCooldown(20, 10, 0.15, 0.95, 8);
        assertEquals(8, reduced);
        // Level 0 / zero config: unchanged.
        assertEquals(20, SpeedMath.reduceCooldown(20, 0, 0.15, 0.8, 2));
        assertEquals(20, SpeedMath.reduceCooldown(20, 3, 0.0, 0.8, 2));
    }
}
