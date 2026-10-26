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
        boolean entityGone = entity == null || !entity.isValid() || entity.isDead();
        Player editor = Bukkit.getPlayer(session.editorUuid());
        boolean editorOnline = editor != null && editor.isOnline();
        String editorName = editorOnline ? editor.getName() : "?";
        boolean isOp = editorOnline && editor.isOp();
        boolean ownerBypass = access.gate().ownerBypassesPermissions(editorName);
        boolean hasUse = editorOnline && editor.hasPermission(StudioPermissions.USE);
        boolean protectedEntity = !entityGone
                && access.protectedEntities().contains(entity.getUniqueId());
        boolean otherPlayer = !entityGone && entity instanceof Player target
                && editorOnline && !target.getUniqueId().equals(editor.getUniqueId());
        boolean hasPlayerEdit = editorOnline
                && editor.hasPermission(StudioPermissions.PLAYER_EDIT);
        boolean concurrent = false;
        if (!entityGone && access.preventConcurrentEditors()) {
            UUID current = editorByEntity.get(entity.getUniqueId());
            concurrent = current != null && editorOnline
                    && !current.equals(editor.getUniqueId());
        }
        Validation v = validateAccess(entityGone, editorOnline,
                access.gate().decide(editorName, isOp), ownerBypass, hasUse,
                protectedEntity, otherPlayer, access.protectOtherPlayers(), hasPlayerEdit,
                concurrent);
        if (v == Validation.ENTITY_GONE || v == Validation.NO_ACCESS && !editorOnline) {
            close(session.editorUuid());
        }
        return v;
    }

    /** Pure authorization matrix (no Bukkit) — unit tested. Mirrors the same
     * rules as {@link #checkTargetAccess} style checks: explicit denials win,
     * the owner bypasses permission nodes but never protected entities. */
    public static Validation validateAccess(boolean entityGone, boolean editorOnline,
            Gatekeeper.Access gateDecision, boolean ownerBypass, boolean hasUse,
            boolean protectedEntity, boolean targetIsOtherPlayer,
            boolean protectOtherPlayers, boolean hasPlayerEdit, boolean concurrentEditor) {
        if (entityGone) {
            return Validation.ENTITY_GONE;
        }
        if (!editorOnline) {
            return Validation.NO_ACCESS;
        }
        if (gateDecision != Gatekeeper.Access.ALLOW) {
            return Validation.NO_ACCESS;
        }
        if (!ownerBypass && !hasUse) {
            return Validation.NO_ACCESS;
        }
        if (protectedEntity) {
            return Validation.PROTECTED;
        }
        if (targetIsOtherPlayer && protectOtherPlayers && !ownerBypass && !hasPlayerEdit) {
            return Validation.NO_ACCESS;
        }
        if (concurrentEditor) {
            return Validation.CONCURRENT_EDITOR;
        }
        return Validation.OK;
    }

    /** Minimal config surface the session layer needs. */
    public interface EntityStudioAccess {
        Gatekeeper gate();

        java.util.Set<UUID> protectedEntities();

        boolean preventConcurrentEditors();

        /** Mirrors settings.security.protect-other-players. */
        boolean protectOtherPlayers();
    }
}
