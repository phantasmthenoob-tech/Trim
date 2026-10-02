package com.unbound.core.math;

import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropMathTest {

    @Test
    void bonusChanceScalesAndCaps() {
        assertEquals(0.0, DropMath.extraBonusChance(0, 0.25));
        assertEquals(0.25, DropMath.extraBonusChance(1, 0.25), 1e-9);
        assertEquals(0.75, DropMath.extraBonusChance(3, 0.25), 1e-9);
        // 0.25 * 4 >= 1.0 -> capped at the 90% design ceiling.
        assertEquals(0.9, DropMath.extraBonusChance(4, 0.25), 1e-9);
        assertEquals(0.9, DropMath.extraBonusChance(10, 0.25));
    }

    @Test
    void rollDuplicateIsDeterministicWithSeed() {
        java.util.random.RandomGenerator alwaysLow = new java.util.Random(0) {
            @Override
            public double nextDouble() {
                return 0.1;
            }
        };
        java.util.random.RandomGenerator alwaysHigh = new java.util.Random(1) {
            @Override
            public double nextDouble() {
                return 0.9;
            }
        };
        assertTrue(DropMath.rollDuplicate(alwaysLow, 0.5));
        assertFalse(DropMath.rollDuplicate(alwaysHigh, 0.5));
        // Degenerate chances.
        assertFalse(DropMath.rollDuplicate(alwaysLow, 0.0));
        assertTrue(DropMath.rollDuplicate(alwaysHigh, 1.0));
    }

    @Test
    void fortuneBlocksIncludeAllOresAndAncientDebris() {
        Set<String> extra = Set.of("glowstone");
        assertTrue(DropMath.isFortuneBlock(Material.DIAMOND_ORE, extra));
        assertTrue(DropMath.isFortuneBlock(Material.DEEPSLATE_DIAMOND_ORE, extra));
        assertTrue(DropMath.isFortuneBlock(Material.NETHER_GOLD_ORE, extra));
        assertTrue(DropMath.isFortuneBlock(Material.ANCIENT_DEBRIS, extra));
        assertTrue(DropMath.isFortuneBlock(Material.GLOWSTONE, extra));
        assertFalse(DropMath.isFortuneBlock(Material.STONE, extra));
        assertFalse(DropMath.isFortuneBlock(Material.DIRT, extra));
    }

    @Test
    void fortuneDropsIncludeRawResourcesAndConfiguredDrops() {
        Set<String> extra = Set.of("glowstone_dust", "nether_wart", "diamond");
        assertTrue(DropMath.isFortuneDrop(Material.RAW_IRON, extra));
        assertTrue(DropMath.isFortuneDrop(Material.RAW_GOLD, extra));
        assertTrue(DropMath.isFortuneDrop(Material.DIAMOND, extra));
        assertTrue(DropMath.isFortuneDrop(Material.GLOWSTONE_DUST, extra));
        assertFalse(DropMath.isFortuneDrop(Material.COBBLESTONE, extra));
        assertFalse(DropMath.isFortuneDrop(Material.DIAMOND_ORE, extra)); // silk touch drops don't qualify
    }
}
