package com.entitystudio.session;

import com.entitystudio.permission.Gatekeeper;
import com.entitystudio.permission.StudioPermissions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks open editing sessions. Before every mutation, callers re-resolve the
 * entity by UUID and re-check authorization — a session never edits a stale or
 * replaced entity, and unloaded/removed entities end the session cleanly.
 */
public class SessionManager {

    public enum Validation {
        OK,
        ENTITY_GONE,
        NO_ACCESS,
        CONCURRENT_EDITOR,
        PROTECTED,
    }

    private final Map<UUID, EditorSession> byEditor = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> editorByEntity = new ConcurrentHashMap<>();

    public EditorSession open(Player editor, Entity entity) {
        close(editor.getUniqueId());
        EditorSession s = new EditorSession(entity.getUniqueId(), editor.getUniqueId(),
                Instant.now(), "dashboard");
        byEditor.put(editor.getUniqueId(), s);
        editorByEntity.put(entity.getUniqueId(), editor.getUniqueId());
        return s;
    }

    public EditorSession get(UUID editorUuid) {
        return byEditor.get(editorUuid);
    }

    public void setPage(UUID editorUuid, String page) {
        byEditor.computeIfPresent(editorUuid, (k, s) -> s.withPage(page));
    }

    public void close(UUID editorUuid) {
        EditorSession s = byEditor.remove(editorUuid);
        if (s != null) {
            editorByEntity.remove(s.entityUuid(), editorUuid);
        }
    }

    public void closeForEntity(UUID entityUuid) {
        UUID editor = editorByEntity.remove(entityUuid);
        if (editor != null) {
            byEditor.remove(editor);
        }
    }

    /** Re-resolves the session target and re-checks every precondition. */
    public Validation validate(EntityStudioAccess access, EditorSession session) {
        Entity entity = Bukkit.getEntity(session.entityUuid());
        if (entity == null || !entity.isValid() || entity.isDead()) {
            close(session.editorUuid());
            return Validation.ENTITY_GONE;
        }
        Player editor = Bukkit.getPlayer(session.editorUuid());
        if (editor == null || !editor.isOnline()) {
            close(session.editorUuid());
            return Validation.NO_ACCESS;
        }
        if (!access.gate().decide(editor.getName(), editor.isOp()).equals(Gatekeeper.Access.ALLOW)) {
            return Validation.NO_ACCESS;
        }
        if (!access.gate().ownerBypassesPermissions(editor.getName())
                && !editor.hasPermission(StudioPermissions.USE)) {
            return Validation.NO_ACCESS;
        }
        if (access.protectedEntities().contains(entity.getUniqueId())) {
            return Validation.PROTECTED;
        }
        if (entity instanceof Player target) {
            if (!target.getUniqueId().equals(editor.getUniqueId())
                    && !access.gate().ownerBypassesPermissions(editor.getName())
                    && !editor.hasPermission(StudioPermissions.PLAYER_EDIT)) {
                return Validation.NO_ACCESS;
            }
        }
        if (access.preventConcurrentEditors()) {
            UUID current = editorByEntity.get(entity.getUniqueId());
            if (current != null && !current.equals(editor.getUniqueId())) {
                return Validation.CONCURRENT_EDITOR;
            }
        }
        return Validation.OK;
    }

    /** Minimal config surface the session layer needs. */
    public interface EntityStudioAccess {
        Gatekeeper gate();

        java.util.Set<UUID> protectedEntities();

        boolean preventConcurrentEditors();
    }
}
