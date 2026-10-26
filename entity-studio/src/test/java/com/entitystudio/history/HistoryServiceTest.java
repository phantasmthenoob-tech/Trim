package com.entitystudio.history;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class HistoryServiceTest {

    private EditRecord record(UUID actor, String label, boolean reversible, Runnable undo) {
        return new EditRecord(actor, UUID.randomUUID(), "ZOMBIE", label, "old", "new",
                reversible, "NORMAL", undo == null ? null : () -> undo.run());
    }

    @Test
    void undoLastRestoresAndPops() {
        HistoryService h = new HistoryService();
        UUID actor = UUID.randomUUID();
        assertEquals(HistoryService.UndoResult.EMPTY, h.undoLast(actor).result());
        AtomicBoolean ran = new AtomicBoolean(false);
        h.record(record(actor, "health", true, () -> ran.set(true)));
        var out = h.undoLast(actor);
        assertEquals(HistoryService.UndoResult.DONE, out.result());
        assertTrue(ran.get());
        assertEquals(0, h.size(actor));
    }

    @Test
    void irreversibleRecordReportedNotUndone() {
        HistoryService h = new HistoryService();
        UUID actor = UUID.randomUUID();
        h.record(record(actor, "remove entity", false, null));
        var out = h.undoLast(actor);
        assertEquals(HistoryService.UndoResult.IRREVERSIBLE, out.result());
        assertEquals(0, h.size(actor));
    }

    @Test
    void failedUndoReported() {
        HistoryService h = new HistoryService();
        UUID actor = UUID.randomUUID();
        h.record(record(actor, "broken", true, () -> {
            throw new IllegalStateException("entity is gone");
        }));
        var out = h.undoLast(actor);
        assertEquals(HistoryService.UndoResult.FAILED, out.result());
        assertTrue(out.detail().contains("entity is gone"));
    }

    @Test
    void stackBounded() {
        HistoryService h = new HistoryService();
        h.setMaxEntries(10);
        UUID actor = UUID.randomUUID();
        for (int i = 0; i < 25; i++) {
            h.record(record(actor, "edit " + i, true, () -> {
            }));
        }
        assertEquals(10, h.size(actor));
        // newest kept first
        assertEquals("edit 24", h.recent(actor, 1).get(0).label());
    }

    @Test
    void peekFirstDoesNotPop() {
        HistoryService h = new HistoryService();
        UUID actor = UUID.randomUUID();
        assertNull(h.peekFirst(actor));
        h.record(record(actor, "first", true, () -> {
        }));
        h.record(record(actor, "second", true, () -> {
        }));
        assertEquals("second", h.peekFirst(actor).label());
        assertEquals(2, h.size(actor));
        // undo still pops the same record peek returned
        assertEquals("second", h.undoLast(actor).record().label());
    }

    @Test
    void recentRespectsLimit() {
        HistoryService h = new HistoryService();
        UUID actor = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            h.record(record(actor, "edit " + i, true, () -> {
            }));
        }
        assertEquals(3, h.recent(actor, 3).size());
    }
}
