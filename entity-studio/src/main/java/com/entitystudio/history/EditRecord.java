package com.entitystudio.history;

import java.time.Instant;
import java.util.UUID;

/** One recorded edit. Non-reversible operations are recorded with undo=null
 * and the UI must say so instead of promising a magical undo button.
 *
 * <p>The timestamp is captured at creation: every undo closure re-resolves
 * its entity by UUID at fire time and never retains a live object reference,
 * so a stale entry can fail safely instead of editing the wrong entity. */
public record EditRecord(UUID actorUuid, UUID entityUuid, String entityType, String label,
        String oldValue, String newValue, boolean reversible, String riskLevel,
        UndoAction undo, Instant timestamp) {

    /** Backwards-compatible creation: stamps the edit time. */
    public EditRecord(UUID actorUuid, UUID entityUuid, String entityType, String label,
            String oldValue, String newValue, boolean reversible, String riskLevel,
            UndoAction undo) {
        this(actorUuid, entityUuid, entityType, label, oldValue, newValue, reversible,
                riskLevel, undo, Instant.now());
    }

    /** Restores the previous value; throws with a reason when it cannot. */
    @FunctionalInterface
    public interface UndoAction {
        void undo() throws Exception;
    }
}
