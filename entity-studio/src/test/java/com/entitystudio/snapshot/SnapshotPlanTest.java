package com.entitystudio.snapshot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotPlanTest {

    @Test
    void schemaDecisions() {
        assertEquals(SnapshotPlan.SchemaDecision.OK, SnapshotPlan.checkSchema(2));
        assertEquals(SnapshotPlan.SchemaDecision.OK, SnapshotPlan.checkSchema(2L));
        assertEquals(SnapshotPlan.SchemaDecision.LEGACY_WARN, SnapshotPlan.checkSchema(1));
        assertEquals(SnapshotPlan.SchemaDecision.LEGACY_WARN, SnapshotPlan.checkSchema(null));
        assertEquals(SnapshotPlan.SchemaDecision.REFUSE, SnapshotPlan.checkSchema(3));
        assertEquals(SnapshotPlan.SchemaDecision.REFUSE, SnapshotPlan.checkSchema(0));
        assertEquals(SnapshotPlan.SchemaDecision.REFUSE, SnapshotPlan.checkSchema(-1));
        assertEquals(SnapshotPlan.SchemaDecision.REFUSE, SnapshotPlan.checkSchema("2"));
        assertEquals(SnapshotPlan.SchemaDecision.REFUSE, SnapshotPlan.checkSchema("future"));
    }

    @Test
    void matchingValuesAreUnchangedNotRewritten() {
        var p = SnapshotPlan.planField("properties", "Silent", "false", "false", true, null);
        assertEquals(SnapshotPlan.Status.UNCHANGED, p.status());
    }

    @Test
    void differingValuesAreChanges() {
        var p = SnapshotPlan.planField("properties", "Silent", "false", "true", true, null);
        assertEquals(SnapshotPlan.Status.CHANGE, p.status());
        assertEquals("false", p.from());
        assertEquals("true", p.to());
    }

    @Test
    void invalidValuesNeverPlanAWrite() {
        var p = SnapshotPlan.planField("properties", "Health", "20.0", "banana", false,
                "expected a number");
        assertEquals(SnapshotPlan.Status.INVALID, p.status());
        assertTrue(p.note().contains("expected a number"));
    }

    @Test
    void unreadableCurrentStillPlansWithNote() {
        var p = SnapshotPlan.planField("properties", "X", null, "1", true, null);
        assertEquals(SnapshotPlan.Status.CHANGE, p.status());
        assertFalse(p.note().isBlank());
    }

    @Test
    void unknownFieldsAreReportedNeverApplied() {
        var p = SnapshotPlan.planUnknown("properties", "some.future.field", "not registered");
        assertEquals(SnapshotPlan.Status.UNKNOWN, p.status());
    }

    @Test
    void sentinelsRecognized() {
        assertTrue(SnapshotPlan.isEmptySlot("__empty__"));
        assertFalse(SnapshotPlan.isEmptySlot(null));
        assertFalse(SnapshotPlan.isEmptySlot("DIAMOND_SWORD"));
        assertTrue(SnapshotPlan.isNoOwner("__none__"));
        assertFalse(SnapshotPlan.isNoOwner("01234567-89ab-cdef-0123-456789abcdef"));
    }

    @Test
    void crossTypeApplicationWarnsSameTypeSilent() {
        assertTrue(SnapshotPlan.typeMismatchWarning("ZOMBIE", "ZOMBIE").isEmpty());
        assertTrue(SnapshotPlan.typeMismatchWarning("zombie", "ZOMBIE").isEmpty());
        var w = SnapshotPlan.typeMismatchWarning("ZOMBIE", "SKELETON");
        assertTrue(w.isPresent());
        assertTrue(w.get().contains("ZOMBIE".toLowerCase()));
        // Unknown source type warns but does not refuse.
        assertTrue(SnapshotPlan.typeMismatchWarning(null, "ZOMBIE").isPresent());
        assertTrue(SnapshotPlan.typeMismatchWarning("", "ZOMBIE").isPresent());
    }

    @Test
    void successfulRunAppliesAllInOrder() throws Exception {
        List<String> order = new ArrayList<>();
        List<SnapshotPlan.PlannedOp> ops = List.of(op("a", order, false), op("b", order, false));
        var out = SnapshotPlan.runAll(ops, List.of());
        assertEquals(2, out.changed());
        assertFalse(out.failedMidway());
        assertEquals(List.of("exec:a", "exec:b"), order);
    }

    @Test
    void midwayFailureRevertsCompletedInReverse() {
        List<String> order = new ArrayList<>();
        List<SnapshotPlan.PlannedOp> ops =
                List.of(op("a", order, false), op("boom", order, true), op("c", order, false));
        var out = SnapshotPlan.runAll(ops, List.of("prior warning"));
        assertTrue(out.failedMidway());
        assertEquals(1, out.changed());
        assertEquals(1, out.reverted());
        assertTrue(out.rollbackErrors().isEmpty());
        assertEquals(List.of("exec:a", "revert:a"), order);
        assertTrue(out.warnings().contains("prior warning"));
        assertTrue(out.warnings().stream().anyMatch(w -> w.contains("boom")));
    }

    @Test
    void revertFailuresAreToleratedAndReported() {
        AtomicInteger reverts = new AtomicInteger();
        SnapshotPlan.PlannedOp bad = new SnapshotPlan.PlannedOp() {
            public SnapshotPlan.FieldPlan plan() {
                return new SnapshotPlan.FieldPlan("s", "bad", "x", "y",
                        SnapshotPlan.Status.CHANGE, "");
            }

            public void execute() {
            }

            public void revert() {
                reverts.incrementAndGet();
                throw new IllegalStateException("gone");
            }
        };
        var out = SnapshotPlan.runAll(List.of(bad, failOp("stop")), List.of());
        assertTrue(out.failedMidway());
        assertEquals(0, out.reverted());
        assertEquals(1, out.rollbackErrors().size());
        assertTrue(out.rollbackErrors().get(0).contains("bad"));
        assertEquals(1, reverts.get());
    }

    // ------------------------------------------------------------- helpers

    private static SnapshotPlan.PlannedOp op(String name, List<String> order, boolean fail) {
        if (fail) {
            return failOp(name);
        }
        return new SnapshotPlan.PlannedOp() {
            public SnapshotPlan.FieldPlan plan() {
                return new SnapshotPlan.FieldPlan("s", name, "x", "y",
                        SnapshotPlan.Status.CHANGE, "");
            }

            public void execute() {
                order.add("exec:" + name);
            }

            public void revert() {
                order.add("revert:" + name);
            }
        };
    }

    private static SnapshotPlan.PlannedOp failOp(String name) {
        return new SnapshotPlan.PlannedOp() {
            public SnapshotPlan.FieldPlan plan() {
                return new SnapshotPlan.FieldPlan("s", name, "x", "y",
                        SnapshotPlan.Status.CHANGE, "");
            }

            public void execute() throws Exception {
                throw new IllegalStateException("midway failure at " + name);
            }

            public void revert() {
            }
        };
    }
}
