package com.entitystudio.behavior;

import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Profile transitions, idempotency, allegiance-family decisions, type-filter
 * parsing and anchor math — all headless. Mob interactions use a dynamic
 * proxy (setters are no-ops); every assertion targets the record and pure
 * helpers, never live server state.
 */
class AllegianceServiceTest {

    /** Proxy Mob: instanceof checks pass, setters do nothing, getters default. */
    static Mob fakeMob() {
        Class<?>[] ifaces = { Mob.class };
        return (Mob) Proxy.newProxyInstance(AllegianceServiceTest.class.getClassLoader(), ifaces,
                (proxy, method, args) -> {
                    Class<?> t = method.getReturnType();
                    if (!t.isPrimitive()) {
                        return null;
                    }
                    if (t == boolean.class) {
                        return false;
                    }
                    if (t == double.class) {
                        return 0.0d;
                    }
                    if (t == float.class) {
                        return 0.0f;
                    }
                    return 0;
                });
    }

    private ManagedRecord fresh() {
        return new ManagedRecord(UUID.randomUUID());
    }

    @Test
    void passiveDisarmsEverything() {
        ManagedRecord r = fresh();
        r.defendOwner(true);
        r.retaliate(true);
        r.attackOwnerTarget(true);
        AllegianceService.applyProfile(fakeMob(), ManagedRecord.Profile.PASSIVE, r);
        assertEquals(ManagedRecord.Profile.PASSIVE, r.profile());
        assertFalse(r.aiEnabled());
        assertFalse(r.defendOwner());
        assertFalse(r.defendAllies());
        assertFalse(r.retaliate());
        assertFalse(r.attackOwnerTarget());
    }

    @Test
    void guardKeepsBrainOnAndDefends() {
        ManagedRecord r = fresh();
        AllegianceService.applyProfile(fakeMob(), ManagedRecord.Profile.GUARD, r);
        assertEquals(ManagedRecord.Profile.GUARD, r.profile());
        assertTrue(r.aiEnabled());
        assertTrue(r.defendOwner());
        assertTrue(r.retaliate());
    }

    @Test
    void neutralIsVanillaRetaliationOnly() {
        ManagedRecord r = fresh();
        AllegianceService.applyProfile(fakeMob(), ManagedRecord.Profile.NEUTRAL, r);
        assertTrue(r.aiEnabled());
        assertFalse(r.defendOwner());
        assertFalse(r.defendAllies());
        assertFalse(r.retaliate());
        assertFalse(r.attackOwnerTarget());
    }

    @Test
    void stationaryIsAiOffWithoutAnchor() {
        ManagedRecord r = fresh();
        AllegianceService.applyProfile(fakeMob(), ManagedRecord.Profile.STATIONARY, r);
        assertFalse(r.aiEnabled());
        assertFalse(r.anchored(), "STATIONARY never anchors by itself");
        assertFalse(r.retaliate());
    }

    @Test
    void aggressiveAndCustomRestoreBrain() {
        for (var p : new ManagedRecord.Profile[] {
                ManagedRecord.Profile.AGGRESSIVE, ManagedRecord.Profile.CUSTOM }) {
            ManagedRecord r = fresh();
            r.aiEnabled(false);
            AllegianceService.applyProfile(fakeMob(), p, r);
            assertTrue(r.aiEnabled(), p.name());
            assertEquals(p, r.profile());
        }
    }

    @Test
    void profilesAreIdempotent() {
        for (var p : ManagedRecord.Profile.values()) {
            ManagedRecord r = fresh();
            r.trusted().add(UUID.randomUUID());
            r.owner(UUID.randomUUID());
            AllegianceService.applyProfile(fakeMob(), p, r);
            // Snapshot the observable record state, apply again, compare.
            boolean ai = r.aiEnabled();
            boolean dO = r.defendOwner(), dA = r.defendAllies();
            boolean ret = r.retaliate(), atk = r.attackOwnerTarget();
            int trusted = r.trusted().size();
            AllegianceService.applyProfile(fakeMob(), p, r);
            assertEquals(ai, r.aiEnabled(), p.name());
            assertEquals(dO, r.defendOwner(), p.name());
            assertEquals(dA, r.defendAllies(), p.name());
            assertEquals(ret, r.retaliate(), p.name());
            assertEquals(atk, r.attackOwnerTarget(), p.name());
            assertEquals(trusted, r.trusted().size(), p.name());
            assertEquals(p, r.profile());
        }
    }

    @Test
    void switchingFromPassiveRestoresBrain() {
        ManagedRecord r = fresh();
        AllegianceService.applyProfile(fakeMob(), ManagedRecord.Profile.PASSIVE, r);
        assertFalse(r.aiEnabled());
        AllegianceService.applyProfile(fakeMob(), ManagedRecord.Profile.GUARD, r);
        assertTrue(r.aiEnabled());
        assertTrue(r.defendOwner());
    }

    @Test
    void sharesAllegianceFamilyRules() {
        UUID owner = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ManagedRecord ra = new ManagedRecord(a);
        ra.owner(owner);
        ManagedRecord rb = new ManagedRecord(b);
        rb.owner(owner);
        assertTrue(AllegianceService.sharesAllegiance(ra, a, rb, b),
                "same non-null owner = family");
        // Explicit enemy on either side always wins over family.
        rb.enemies().add(a);
        assertFalse(AllegianceService.sharesAllegiance(ra, a, rb, b));
        rb.enemies().clear();
        ra.enemies().add(b);
        assertFalse(AllegianceService.sharesAllegiance(ra, a, rb, b));
        ra.enemies().clear();
        // Protection off on either side breaks family.
        rb.friendlyFireProtect(false);
        assertFalse(AllegianceService.sharesAllegiance(ra, a, rb, b));
        rb.friendlyFireProtect(true);
        assertTrue(AllegianceService.sharesAllegiance(ra, a, rb, b));
        // Mutual trust without a shared owner also counts.
        ManagedRecord rx = new ManagedRecord(a);
        ManagedRecord ry = new ManagedRecord(b);
        rx.trusted().add(b);
        ry.trusted().add(a);
        assertTrue(AllegianceService.sharesAllegiance(rx, a, ry, b));
        // One-sided claim from a lone record counts; strangers do not.
        ManagedRecord lone = new ManagedRecord(a);
        lone.trusted().add(b);
        assertTrue(AllegianceService.sharesAllegiance(lone, a, null, b));
        assertFalse(AllegianceService.sharesAllegiance(null, a, null, b));
        assertFalse(AllegianceService.sharesAllegiance(ra, a, ra, a), "self is never family");
    }

    @Test
    void typeFilterParseAcceptsAndRejects() {
        var ok = AllegianceService.parseTypeFilter("zombie, SKELETON");
        assertTrue(ok.ok());
        assertEquals(Set.of("ZOMBIE", "SKELETON"), ok.types());
        var blank = AllegianceService.parseTypeFilter("   ");
        assertTrue(blank.ok());
        assertTrue(blank.types().isEmpty());
        var bad = AllegianceService.parseTypeFilter("zombie, not_a_mob");
        assertFalse(bad.ok());
        assertTrue(bad.error().contains("not_a_mob"));
        assertNull(bad.types(), "a filter never half-applies");
    }

    @Test
    void anchorMathThresholds() {
        assertFalse(AllegianceService.shouldCorrectHard(0.0));
        assertFalse(AllegianceService.shouldCorrectHard(0.04));
        assertTrue(AllegianceService.shouldCorrectHard(0.05));
        assertFalse(AllegianceService.shouldSettleSoft(8.99, 3.0));
        assertTrue(AllegianceService.shouldSettleSoft(9.01, 3.0));
    }

    @Test
    void recordDefaultsAreConservative() {
        ManagedRecord r = fresh();
        assertEquals(ManagedRecord.Profile.CUSTOM, r.profile());
        assertTrue(r.aiEnabled());
        assertTrue(r.defendOwner());
        assertFalse(r.defendAllies());
        assertFalse(r.retaliate());
        assertFalse(r.attackOwnerTarget());
        assertTrue(r.friendlyFireProtect());
        assertTrue(r.clearInvalidTarget());
        assertFalse(r.anchored());
    }
}
