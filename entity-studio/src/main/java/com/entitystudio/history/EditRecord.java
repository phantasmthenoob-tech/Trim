package com.entitystudio.history;

import java.time.Instant;
import java.util.UUID;

/** One recorded edit. Non-reversible operations are recorded with undo=null
 * and the UI must say so instead of promising a magical undo button. */
public record EditRecord(UUID actorUuid, UUID entityUuid, String entityType, String label,
        String oldValue, String newValue, boolean reversible, String riskLevel,
        UndoAction undo) {

    public Instant timestamp() {
        return Instant.now();
    }

    /** Restores the previous value; throws with a reason when it cannot. */
    @FunctionalInterface
    public interface UndoAction {
        void undo() throws Exception;
    }
}
