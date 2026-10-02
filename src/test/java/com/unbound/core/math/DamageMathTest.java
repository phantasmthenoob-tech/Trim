package com.unbound.core.math;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageMathTest {

    @Test
    void vanillaSharpnessFollowsVanillaFormula() {
        assertEquals(0.0, DamageMath.sharpnessVanillaBonus(0));
        assertEquals(1.0, DamageMath.sharpnessVanillaBonus(1));
        assertEquals(1.5, DamageMath.sharpnessVanillaBonus(2));
        assertEquals(5.5, DamageMath.sharpnessVanillaBonus(10));
    }

    @Test
    void defaultConfigMatchesVanillaSoTheDeltaIsZero() {
        // Defaults: base 1.0, per-level 0.5 -> exactly vanilla
        // (vanilla = 0.5 * level + 0.5), no double apply.
        for (int level = 1; level <= 10; level++) {
            assertEquals(0.0, DamageMath.sharpnessDelta(level, 1.0, 0.5), 1e-9);
        }
    }

    @Test
    void customScalingReshapesSharpnessWithoutDoubleCounting() {
        // base 1.0, per-level 1.0 -> level 3: our 3.0, vanilla 2.0, delta 1.0.
        assertEquals(1.0, DamageMath.sharpnessDelta(3, 1.0, 1.0), 1e-9);
        // Weaker scaling is allowed too (delta negative).
        assertTrue(DamageMath.sharpnessDelta(5, 0.5, 0.25) < 0.0);
    }

    @Test
    void projectileDamageScalesLinearlyPerLevel() {
        assertEquals(0.0, DamageMath.projectileDamageBonus(0, 0.5));
        assertEquals(0.5, DamageMath.projectileDamageBonus(1, 0.5));
        assertEquals(5.0, DamageMath.projectileDamageBonus(10, 0.5));
    }

    @Test
    void punchVelocityPushesVictimAwayFromAttacker() {
        // Attacker at origin, victim to the +x side.
        Vector push = DamageMath.punchVelocity(0, 0, 10, 0, 0.4);
        assertTrue(push.getX() > 0);
        assertEquals(0.4, push.getX(), 1e-9);
        assertEquals(0, push.getZ(), 1e-9);
        // Small vertical lift, always positive.
        assertTrue(push.getY() >= 0.2);

        Vector pushZ = DamageMath.punchVelocity(0, 0, 0, -3, 0.6);
        assertTrue(pushZ.getZ() < 0);
        assertEquals(0.6, Math.abs(pushZ.getZ()), 1e-9);
    }

    @Test
    void punchVelocityHandlesIdenticalPositions() {
        Vector push = DamageMath.punchVelocity(1, 1, 1, 1, 0.4);
        assertEquals(0, push.getX(), 1e-9);
        assertEquals(0, push.getZ(), 1e-9);
        assertTrue(push.getY() >= 0.2);
    }
}
