package com.entitystudio.session;

import com.entitystudio.permission.Gatekeeper;
import org.junit.jupiter.api.Test;

import static com.entitystudio.session.SessionManager.Validation.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    private SessionManager.Validation ok(boolean protectOthers) {
        return SessionManager.validateAccess(false, true, Gatekeeper.Access.ALLOW,
                false, true, false, false, protectOthers, false, false);
    }

    @Test
    void happyPathAllows() {
        assertEquals(OK, ok(true));
        assertEquals(OK, ok(false));
    }

    @Test
    void goneEntityAlwaysFirst() {
        assertEquals(ENTITY_GONE, SessionManager.validateAccess(true, false,
                Gatekeeper.Access.NEED_START, false, false, true, true, true, false, true));
    }

    @Test
    void offlineEditorDenied() {
        assertEquals(NO_ACCESS, SessionManager.validateAccess(false, false,
                Gatekeeper.Access.ALLOW, true, true, false, false, true, true, false));
    }

    @Test
    void lockedPluginDeniesEvenOwnerBypass() {
        // Owner bypass skips permission nodes, never the lock decision itself:
        // decide() already returned NEED_START for this non-owner.
        assertEquals(NO_ACCESS, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.NEED_START, false, true, false, false, true, true, false));
    }

    @Test
    void ownerBypassSkipsUseNode() {
        assertEquals(OK, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.ALLOW, true, false, false, false, true, false, false));
    }

    @Test
    void missingUseNodeDenied() {
        assertEquals(NO_ACCESS, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.ALLOW, false, false, false, false, true, false, false));
    }

    @Test
    void protectedEntityBeatsEverythingButGone() {
        assertEquals(PROTECTED, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.ALLOW, true, true, true, false, true, true, false));
    }

    @Test
    void otherPlayerNeedsPlayerEditWhenProtected() {
        assertEquals(NO_ACCESS, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.ALLOW, false, true, false, true, true, false, false));
        assertEquals(OK, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.ALLOW, false, true, false, true, true, true, false));
    }

    @Test
    void otherPlayerAllowedWhenProtectionDisabled() {
        // Mirrors settings.security.protect-other-players: false.
        assertEquals(OK, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.ALLOW, false, true, false, true, false, false, false));
    }

    @Test
    void ownerBypassesPlayerEditCheck() {
        assertEquals(OK, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.ALLOW, true, true, false, true, true, false, false));
    }

    @Test
    void concurrentEditorBlocked() {
        assertEquals(CONCURRENT_EDITOR, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.ALLOW, false, true, false, false, true, false, true));
    }

    @Test
    void selfEditingIsNotOtherPlayer() {
        assertEquals(OK, SessionManager.validateAccess(false, true,
                Gatekeeper.Access.ALLOW, false, true, false, false, true, false, false));
    }
}
