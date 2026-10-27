package com.entitystudio.mobform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormStatsTest {

    @Test
    void namedMobsHaveSaneStats() {
        FormStats.Stats evoker = FormStats.forType("EVOKER");
        assertEquals(24.0, evoker.health());
        assertTrue(evoker.primaryCooldownMs() > 0);
        assertTrue(evoker.secondaryCooldownMs() > 0);
        assertNotNull(evoker.secondaryDesc());

        FormStats.Stats blaze = FormStats.forType("BLAZE");
        assertTrue(blaze.fly());
        assertTrue(blaze.fireImmune());

        FormStats.Stats sheep = FormStats.forType("sheep");
        assertEquals(8.0, sheep.health());
        assertTrue(sheep.scale() < 1.0);
    }

    @Test
    void unknownMobsGetSafeFallback() {
        FormStats.Stats s = FormStats.forType("SNIFFER");
        assertEquals(20.0, s.health());
        assertEquals(1.0, s.scale());
        assertFalse(s.fly());
        assertNotNull(s.primaryDesc());
        assertNull(s.secondaryDesc());
        assertFalse(FormStats.isDetailed("SNIFFER"));
        assertTrue(FormStats.isDetailed("CREEPER"));
    }

    @Test
    void nullIsSafe() {
        assertNotNull(FormStats.forType(null));
        assertFalse(FormStats.isDetailed(null));
    }

    @Test
    void cooldownsArePositiveWhereAbilitiesExist() {
        for (String type : new String[] { "EVOKER", "BLAZE", "SHEEP", "CREEPER",
                "ENDERMAN", "SKELETON", "ZOMBIE", "SPIDER", "SLIME", "GHAST",
                "WITHER", "ENDER_DRAGON" }) {
            assertTrue(FormStats.forType(type).primaryCooldownMs() > 0, type);
        }
    }
}
