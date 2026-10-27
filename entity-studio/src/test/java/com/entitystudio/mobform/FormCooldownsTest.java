package com.entitystudio.mobform;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class FormCooldownsTest {

    @Test
    void firstUseAlwaysAllowed() {
        FormCooldowns cd = new FormCooldowns(() -> 1_000L);
        assertTrue(cd.tryUse(UUID.randomUUID(), "EVOKER:primary", 5_000L));
    }

    @Test
    void secondUseBlockedUntilExpiry() {
        AtomicLong clock = new AtomicLong(1_000L);
        FormCooldowns cd = new FormCooldowns(clock::get);
        UUID player = UUID.randomUUID();
        assertTrue(cd.tryUse(player, "F", 5_000L));
        clock.set(3_000L);
        assertFalse(cd.tryUse(player, "F", 5_000L));
        assertEquals(3_000L, cd.remainingMs(player, "F"));
        clock.set(6_001L);
        assertEquals(0L, cd.remainingMs(player, "F"));
        assertTrue(cd.tryUse(player, "F", 5_000L));
        assertEquals(5_000L, cd.remainingMs(player, "F"));
    }

    @Test
    void keysAreIndependentPerPlayerAndAbility() {
        AtomicLong clock = new AtomicLong(0L);
        FormCooldowns cd = new FormCooldowns(clock::get);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(cd.tryUse(a, "primary", 5_000L));
        assertTrue(cd.tryUse(a, "secondary", 5_000L));
        assertTrue(cd.tryUse(b, "primary", 5_000L));
        assertFalse(cd.tryUse(a, "primary", 5_000L));
    }

    @Test
    void zeroCooldownNeverBlocks() {
        FormCooldowns cd = new FormCooldowns(() -> 0L);
        UUID player = UUID.randomUUID();
        assertTrue(cd.tryUse(player, "F", 0L));
        assertTrue(cd.tryUse(player, "F", 0L));
    }

    @Test
    void clearResetsPlayer() {
        AtomicLong clock = new AtomicLong(0L);
        FormCooldowns cd = new FormCooldowns(clock::get);
        UUID player = UUID.randomUUID();
        assertTrue(cd.tryUse(player, "F", 60_000L));
        assertFalse(cd.tryUse(player, "F", 60_000L));
        cd.clear(player);
        assertTrue(cd.tryUse(player, "F", 60_000L));
    }
}
