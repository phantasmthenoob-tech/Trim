package com.entitystudio.behavior;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.entitystudio.behavior.AllegianceService.sharesAllegiance;
import static org.junit.jupiter.api.Assertions.*;

class AllegianceServiceTest {

    private ManagedRecord record(UUID owner, UUID... trusted) {
        ManagedRecord r = new ManagedRecord(UUID.randomUUID());
        if (owner != null) {
            r.owner(owner);
        }
        for (UUID t : trusted) {
            r.trusted().add(t);
        }
        return r;
    }

    @Test
    void sameOwnerCountsAsFamily() {
        UUID owner = UUID.randomUUID();
        ManagedRecord a = record(owner);
        ManagedRecord b = record(owner);
        UUID aid = UUID.randomUUID();
        UUID bid = UUID.randomUUID();
        assertTrue(sharesAllegiance(a, aid, b, bid));
    }

    @Test
    void differentOwnersAreNotFamily() {
        ManagedRecord a = record(UUID.randomUUID());
        ManagedRecord b = record(UUID.randomUUID());
        assertFalse(sharesAllegiance(a, UUID.randomUUID(), b, UUID.randomUUID()));
    }

    @Test
    void mutualTrustCountsAsFamily() {
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        ManagedRecord a = record(UUID.randomUUID());
        a.trusted().add(y);
        ManagedRecord b = record(UUID.randomUUID());
        b.trusted().add(x);
        assertTrue(sharesAllegiance(a, x, b, y));
    }

    @Test
    void oneSidedClaimFromManagedSideCounts() {
        UUID owner = UUID.randomUUID();
        UUID guard = UUID.randomUUID();
        ManagedRecord r = record(owner);
        // owner (unmanaged) hits own guard: family
        assertTrue(sharesAllegiance(null, owner, r, guard));
        // guard (managed) hits owner: family
        assertTrue(sharesAllegiance(r, guard, null, owner));
    }

    @Test
    void enemyMarkingBeatsFamily() {
        UUID owner = UUID.randomUUID();
        UUID rogue = UUID.randomUUID();
        ManagedRecord a = record(owner);
        ManagedRecord b = record(owner);
        b.enemies().add(rogue);
        assertFalse(sharesAllegiance(a, rogue, b, UUID.randomUUID()));
    }

    @Test
    void disabledProtectionOptsOut() {
        UUID owner = UUID.randomUUID();
        ManagedRecord a = record(owner);
        ManagedRecord b = record(owner);
        b.friendlyFireProtect(false);
        assertFalse(sharesAllegiance(a, UUID.randomUUID(), b, UUID.randomUUID()));
    }

    @Test
    void selfIsNeverFamily() {
        UUID id = UUID.randomUUID();
        ManagedRecord a = record(UUID.randomUUID());
        assertFalse(sharesAllegiance(a, id, a, id));
    }

    @Test
    void unmanagedStrangersAreNotFamily() {
        assertFalse(sharesAllegiance(null, UUID.randomUUID(), null, UUID.randomUUID()));
    }
}
