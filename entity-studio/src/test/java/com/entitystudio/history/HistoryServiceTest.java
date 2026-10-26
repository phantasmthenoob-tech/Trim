package com.entitystudio.history;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
    void sequentialEditsUndoNewestFirst() {
        HistoryService h = new HistoryService();
        UUID actor = UUID.randomUUID();
        List<String> undone = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final String label = "edit " + i;
            h.record(record(actor, label, true, () -> undone.add(label)));
        }
        // Repeated undo walks back in exact reverse order.
        for (int i = 4; i >= 0; i--) {
            var out = h.undoLast(actor);
            assertEquals(HistoryService.UndoResult.DONE, out.result());
            assertEquals("edit " + i, out.record().label());
        }
        assertEquals(List.of("edit 4", "edit 3", "edit 2", "edit 1", "edit 0"), undone);
        assertEquals(HistoryService.UndoResult.EMPTY, h.undoLast(actor).result());
    }

    @Test
    void irreversiblePopsAndOlderUndoStillWorks() {
        HistoryService h = new HistoryService();
        UUID actor = UUID.randomUUID();
        AtomicBoolean ran = new AtomicBoolean(false);
        h.record(record(actor, "older reversible", true, () -> ran.set(true)));
        h.record(record(actor, "newer destructive", false, null));
        // The irreversible entry is popped and reported; the older
        // reversible edit directly beneath it remains undoable (partial
        // recovery — nothing is skipped or silently merged).
        var first = h.undoLast(actor);
        assertEquals(HistoryService.UndoResult.IRREVERSIBLE, first.result());
        assertEquals("newer destructive", first.record().label());
        var second = h.undoLast(actor);
        assertEquals(HistoryService.UndoResult.DONE, second.result());
        assertTrue(ran.get());
    }

    @Test
    void invalidEntriesDroppedSafely() {
        HistoryService h = new HistoryService();
        UUID actor = UUID.randomUUID();
        assertDoesNotThrow(() -> h.record(null));
        assertDoesNotThrow(() -> h.record(new EditRecord(null, UUID.randomUUID(),
                "ZOMBIE", "no actor", "o", "n", true, "NORMAL", () -> {
                })));
        assertDoesNotThrow(() -> h.record(new EditRecord(actor, null,
                "ZOMBIE", "no entity", "o", "n", true, "NORMAL", () -> {
                })));
        assertEquals(0, h.size(actor));
        assertEquals(HistoryService.UndoResult.EMPTY, h.undoLast(actor).result());
        assertEquals(HistoryService.UndoResult.EMPTY, h.undoLast(null).result());
        assertEquals(0, h.size(null));
        assertTrue(h.recent(null, 5).isEmpty());
        assertNull(h.peekFirst(null));
        assertDoesNotThrow(() -> h.clear(null));
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
