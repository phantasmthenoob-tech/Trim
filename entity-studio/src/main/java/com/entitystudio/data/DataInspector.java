package com.entitystudio.data;

import org.bukkit.entity.Entity;

import java.util.List;

/**
 * Narrow seam for advanced-data reads. Implementations may only use the public
 * Bukkit/Paper API; version-specific internals (NMS, reflection) are not used.
 * Keeping every read behind this interface means a future version-specific
 * probe can be added — and fail closed — without touching the GUI.
 */
public interface DataInspector {

    /**
     * Reads every safely readable fact about an entity.
     *
     * @param entity the live entity; getters only, never mutated
     * @param canSeePrivateData false when the target is another player and the
     * viewer lacks {@code entitystudio.player.edit}; player PDC/SNBT/tags are
     * then reported as REDACTED instead of shown
     * @return ordered rows; never null, never throws for getter failures
     * (those become UNAVAILABLE rows)
     */
    List<DataField> inspect(Entity entity, boolean canSeePrivateData);
}
