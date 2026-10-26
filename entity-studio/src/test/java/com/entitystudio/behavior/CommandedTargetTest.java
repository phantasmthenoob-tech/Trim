package com.entitystudio.behavior;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Commanded-target memory: owner orders stick until the target is gone. */
class CommandedTargetTest {

    @Test
    void reassertRangeGate() {
        assertTrue(AllegianceService.shouldReassert(true, 0));
        assertTrue(AllegianceService.shouldReassert(true,
                AllegianceService.REASSERT_RANGE * AllegianceService.REASSERT_RANGE));
        assertFalse(AllegianceService.shouldReassert(true,
                AllegianceService.REASSERT_RANGE * AllegianceService.REASSERT_RANGE + 1));
        assertFalse(AllegianceService.shouldReassert(false, 0));
        assertFalse(AllegianceService.shouldReassert(true, -1));
    }

    @Test
    void commandLifecycle() {
        ManagedRecord r = new ManagedRecord(UUID.randomUUID());
        assertNull(r.commandedTarget());
        UUID target = UUID.randomUUID();
        r.command(target, 123L);
        assertEquals(target, r.commandedTarget());
        assertEquals(123L, r.commandedAt());
        r.clearCommand();
        assertNull(r.commandedTarget());
        assertEquals(0L, r.commandedAt());
    }
}
