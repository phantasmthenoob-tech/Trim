package com.unbound.core.enchant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingGuardTest {

    private static final ProcessingGuard.Limits LIMITS = new ProcessingGuard.Limits(3, 5, 2, 4);

    @AfterEach
    void cleanUp() {
        while (ProcessingGuard.isActive()) {
            ProcessingGuard.exit();
        }
    }

    @Test
    void rootScopeIsNotSyntheticAndStartsClean() {
        ProcessingGuard guard = ProcessingGuard.enter(LIMITS);
        assertEquals(0, guard.depth());
        assertFalse(guard.isSynthetic());
        assertFalse(guard.isDepthExceeded());
        assertFalse(guard.isSynthetic());
        assertEquals(0, guard.entitiesAffected());
        assertEquals(0, guard.blocksAffected());
        assertEquals(0, guard.projectilesCreated());
    }

    @Test
    void nestedScopeIsSyntheticAndDeeper() {
        ProcessingGuard root = ProcessingGuard.enter(LIMITS);
        ProcessingGuard child = ProcessingGuard.enter(LIMITS);
        assertEquals(1, child.depth());
        assertTrue(child.isSynthetic());
        assertFalse(root.isSynthetic());
        ProcessingGuard.exit();
        assertEquals(0, ProcessingGuard.current().depth());
    }

    @Test
    void depthLimitStopsChaining() {
        // Root scope is the top-level action (depth 0); every nested
        // synthetic event opens a deeper scope.
        ProcessingGuard.enter(LIMITS);
        for (int i = 0; i < LIMITS.maxDepth(); i++) {
            ProcessingGuard.enter(LIMITS);
        }
        ProcessingGuard innermost = ProcessingGuard.current();
        assertEquals(LIMITS.maxDepth(), innermost.depth());
        assertTrue(innermost.isDepthExceeded());
    }

    @Test
    void entityBudgetIsCappedAndSharedAcrossScopes() {
        ProcessingGuard root = ProcessingGuard.enter(LIMITS);
        ProcessingGuard child = ProcessingGuard.enter(LIMITS);
        assertEquals(3, child.consumeEntitySlots(10));
        assertEquals(0, child.consumeEntitySlots(1));
        assertEquals(3, root.entitiesAffected());
        ProcessingGuard.exit();
        assertEquals(0, root.consumeEntitySlots(1));
    }

    @Test
    void blockAndProjectileBudgetsAreCapped() {
        ProcessingGuard guard = ProcessingGuard.enter(LIMITS);
        assertEquals(5, guard.consumeBlockSlots(99));
        assertEquals(2, guard.consumeProjectileSlots(2));
        assertFalse(guard.hasProjectileBudget());
        assertFalse(guard.hasBlockBudget());
    }

    @Test
    void exitRestoresParentAndClearsRoot() {
        ProcessingGuard.enter(LIMITS);
        ProcessingGuard.enter(LIMITS);
        ProcessingGuard.exit();
        assertTrue(ProcessingGuard.isActive());
        ProcessingGuard.exit();
        assertFalse(ProcessingGuard.isActive());
        ProcessingGuard.exit(); // no-op, must not throw
        assertFalse(ProcessingGuard.isActive());
    }
}
