package com.unbound.core.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurabilityMathTest {

    @Test
    void vanillaSkipChanceMatchesVanillaFormula() {
        assertEquals(0.0, DurabilityMath.vanillaSkipChance(0));
        assertEquals(0.64, DurabilityMath.vanillaSkipChance(1), 1e-9);
        assertEquals(0.68, DurabilityMath.vanillaSkipChance(2), 1e-9);
        assertEquals(1.0, DurabilityMath.vanillaSkipChance(10)); // capped
    }

    @Test
    void extraSkipChanceDefaultsToZeroAndScales() {
        assertEquals(0.0, DurabilityMath.extraSkipChance(5, 0.0));
        assertEquals(0.0, DurabilityMath.extraSkipChance(0, 0.1));
        assertEquals(0.15, DurabilityMath.extraSkipChance(1, 0.15), 1e-9);
        assertEquals(0.6, DurabilityMath.extraSkipChance(4, 0.15), 1e-9);
    }

    @Test
    void extraSkipChanceNeverMakesItemsIndestructible() {
        // Hard cap at 90% regardless of configuration.
        assertEquals(0.9, DurabilityMath.extraSkipChance(100, 0.5));
        assertEquals(0.9, DurabilityMath.extraSkipChance(10, 10.0));
    }

    @Test
    void infinityDurabilityPrecedenceIsDeterministic() {
        assertTrue(DurabilityMath.infinityPreventsDamage(true, true));
        assertFalse(DurabilityMath.infinityPreventsDamage(true, false));
        assertFalse(DurabilityMath.infinityPreventsDamage(false, true));
        assertFalse(DurabilityMath.infinityPreventsDamage(false, false));
    }

    @Test
    void mendingRepairsFullItemWithLeftoverXp() {
        // Item missing 10 durability, 10 XP available at 2 durability/XP:
        // needs 5 XP, 5 XP remain.
        DurabilityMath.MendResult result = DurabilityMath.mend(10, 10, 2.0);
        assertEquals(10, result.durabilityRepaired());
        assertEquals(5, result.xpSpent());
        assertTrue(result.didRepair());
    }

    @Test
    void mendingSpendsAllXpOnDeepDamage() {
        // Item missing 100 durability, 4 XP: repairs 8, spends all 4.
        DurabilityMath.MendResult result = DurabilityMath.mend(100, 4, 2.0);
        assertEquals(8, result.durabilityRepaired());
        assertEquals(4, result.xpSpent());
    }

    @Test
    void mendingHandlesFractionalRatesWithoutXpLoss() {
        // 1.5 durability per XP: item missing 3 needs 2 XP.
        DurabilityMath.MendResult result = DurabilityMath.mend(3, 10, 1.5);
        assertEquals(3, result.durabilityRepaired());
        assertEquals(2, result.xpSpent());
    }

    @Test
    void mendingIgnoresImpossibleInputs() {
        assertEquals(DurabilityMath.MendResult.NONE, DurabilityMath.mend(0, 10, 2.0));
        assertEquals(DurabilityMath.MendResult.NONE, DurabilityMath.mend(10, 0, 2.0));
        assertEquals(DurabilityMath.MendResult.NONE, DurabilityMath.mend(10, 10, 0.0));
    }
}
