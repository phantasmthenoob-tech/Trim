package com.entitystudio.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bukkit-free planning core for snapshots and presets.
 *
 * <p>Merge semantics (enforced by the service, defined here):
 * <ul>
 *   <li>Absent fields are never planned — the target keeps its value.</li>
 *   <li>Explicit resets use intentional sentinels ({@link #EMPTY_SLOT},
 *   {@link #NO_OWNER}) because YAML round-trips cannot distinguish an
 *   explicit null from a missing key.</li>
 *   <li>Unknown fields are planned as {@code UNKNOWN}: reported, never applied.</li>
 *   <li>Unknown schema versions refuse the whole apply, not just the field.</li>
 * </ul>
 */
public final class SnapshotPlan {

    /** Current snapshot schema. v1 files (no attributes/effects) still read. */
    public static final int SCHEMA = 2;
    /** Stored equipment-slot value meaning "intentionally clear this slot". */
    public static final String EMPTY_SLOT = "__empty__";
    /** Stored owner value meaning "intentionally leave/clear the owner". */
    public static final String NO_OWNER = "__none__";

    public enum Status {
        /** Value differs and will be written. */
        CHANGE,
        /** Already equal — a no-op write is skipped. */
        UNCHANGED,
        /** Target does not support this field — skipped, reported. */
        UNSUPPORTED,
        /** Stored value fails validation — skipped, reported. */
        INVALID,
        /** Not a known field — skipped, reported, never applied. */
        UNKNOWN,
        /** Informational: applied but with an irreversible side note. */
        INFO,
    }

    public record FieldPlan(String section, String label, String from, String to,
            Status status, String note) {
    }

    public enum SchemaDecision {
        OK,
        /** No version stamped (pre-schema files): readable, warn once. */
        LEGACY_WARN,
        /** Anything else: refuse the entire apply. */
        REFUSE,
    }

    private SnapshotPlan() {
    }

    public static SchemaDecision checkSchema(Object stored) {
        if (stored == null) {
            return SchemaDecision.LEGACY_WARN;
        }
        if (stored instanceof Number n) {
            int v = n.intValue();
            if (v == SCHEMA) {
                return SchemaDecision.OK;
            }
            if (v == 1) {
                return SchemaDecision.LEGACY_WARN;
            }
        }
        return SchemaDecision.REFUSE;
    }

    /**
     * Plans one present stored field. {@code current} may be null when the
     * live value is unreadable — then the write proceeds (CHANGE) with a
     * note, since refusal would make unreadable-then-writable fields
     * permanently unplannable.
     */
    public static FieldPlan planField(String section, String label, String current,
            String target, boolean valid, String invalidReason) {
        if (!valid) {
            return new FieldPlan(section, label, disp(current), disp(target),
                    Status.INVALID, invalidReason == null ? "invalid value" : invalidReason);
        }
        if (current == null) {
            return new FieldPlan(section, label, "?", disp(target), Status.CHANGE,
                    "current value unreadable — write attempted");
        }
        if (current.equals(target)) {
            return new FieldPlan(section, label, current, target, Status.UNCHANGED,
                    "already matches");
        }
        return new FieldPlan(section, label, current, target, Status.CHANGE, "");
    }

    public static FieldPlan planUnknown(String section, String label, String note) {
        return new FieldPlan(section, label, "—", "—", Status.UNKNOWN, note);
    }

    public static FieldPlan planUnsupported(String section, String label, String current,
            String note) {
        return new FieldPlan(section, label, disp(current), "—", Status.UNSUPPORTED, note);
    }

    private static String disp(String s) {
        return s == null ? "—" : s;
    }

    public static boolean isEmptySlot(Object raw) {
        return EMPTY_SLOT.equals(String.valueOf(raw));
    }

    public static boolean isNoOwner(Object raw) {
        return NO_OWNER.equals(String.valueOf(raw));
    }

    /** Empty when the stored source type matches (or is unknown); warns otherwise. */
    public static Optional<String> typeMismatchWarning(String storedType, String targetType) {
        if (storedType == null || storedType.isBlank()) {
            return Optional.of("Preset has no recorded entity type — applying field-by-field"
                    + " to " + targetType.toLowerCase() + "; unsupported fields are skipped.");
        }
        if (storedType.equalsIgnoreCase(targetType)) {
            return Optional.empty();
        }
        return Optional.of("Preset was captured from " + storedType.toLowerCase()
                + " but the target is " + targetType.toLowerCase()
                + " — only supported fields apply.");
    }

    // ------------------------------------------------------- execution order

    /** One validated, executable change with its own best-effort revert. */
    public interface PlannedOp {
        FieldPlan plan();

        void execute() throws Exception;

        void revert() throws Exception;
    }

    public record ApplyOutcome(int changed, int skipped, List<String> warnings,
            boolean failedMidway, int reverted, List<String> rollbackErrors) {
    }

    /**
     * Runs validated ops in order. On the first failure, completed ops are
     * reverted in reverse (revert failures are tolerated and reported, never
     * thrown). This is best-effort, not transactional: operations the
     * platform cannot reverse stay changed, and the outcome says so.
     */
    public static ApplyOutcome runAll(List<PlannedOp> ops, List<String> warnings) {
        List<PlannedOp> done = new ArrayList<>();
        List<String> notes = new ArrayList<>(warnings);
        int changed = 0;
        boolean failedMidway = false;
        int reverted = 0;
        List<String> rollbackErrors = new ArrayList<>();
        for (PlannedOp op : ops) {
            try {
                op.execute();
                done.add(op);
                changed++;
            } catch (Exception e) {
                failedMidway = true;
                notes.add("Stopped at '" + op.plan().label() + "': "
                        + (e.getMessage() == null ? e.toString() : e.getMessage()));
                break;
            }
        }
        if (failedMidway) {
            for (int i = done.size() - 1; i >= 0; i--) {
                PlannedOp op = done.get(i);
                try {
                    op.revert();
                    reverted++;
                } catch (Exception e) {
                    rollbackErrors.add("Could not roll back '" + op.plan().label() + "': "
                            + (e.getMessage() == null ? e.toString() : e.getMessage()));
                }
            }
            notes.add("Rolled back " + reverted + "/" + done.size()
                    + " applied change(s) — best-effort, not transactional.");
        }
        return new ApplyOutcome(changed, 0, List.copyOf(notes), failedMidway, reverted,
                List.copyOf(rollbackErrors));
    }
}
