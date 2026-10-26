package com.entitystudio.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatekeeperTest {

    @Test
    void ownerAlwaysAllowedEvenWhenLocked() {
        Gatekeeper g = new Gatekeeper("ImNotAllocate", false);
        assertTrue(g.isOwner("ImNotAllocate"));
        assertTrue(g.isOwner("imnotallocate")); // case-insensitive
        assertEquals(Gatekeeper.Access.ALLOW, g.decide("ImNotAllocate", false));
        assertEquals(Gatekeeper.Access.ALLOW, g.decide("ImNotAllocate", true));
    }

    @Test
    void opsBlockedUntilStart() {
        Gatekeeper g = new Gatekeeper("ImNotAllocate", false);
        assertEquals(Gatekeeper.Access.NEED_START, g.decide("SomeOp", true));
        assertEquals(Gatekeeper.Access.NEED_START, g.decide("RandomPlayer", false));
    }

    @Test
    void opsAllowedAfterStart() {
        Gatekeeper g = new Gatekeeper("ImNotAllocate", false);
        g.setActive(true);
        assertEquals(Gatekeeper.Access.ALLOW, g.decide("SomeOp", true));
        assertEquals(Gatekeeper.Access.ALLOW, g.decide("RandomPlayer", false));
    }

    @Test
    void onlyOwnerOrConsoleCanStart() {
        Gatekeeper g = new Gatekeeper("ImNotAllocate", false);
        assertTrue(g.canStart("ImNotAllocate", false));
        assertFalse(g.canStart("SomeOp", false));
        assertTrue(g.canStart(null, true)); // console
    }

    @Test
    void emptyOwnerDisablesOverride() {
        Gatekeeper g = new Gatekeeper("", false);
        assertFalse(g.isOwner("ImNotAllocate"));
        assertFalse(g.canStart("ImNotAllocate", false));
    }
}
