package com.entitystudio.behavior;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Offspring/summon inheritance: bloodline copies everything persistent,
 * never position- or fight-specific state; donor search is nearest-in-range. */
class InheritanceTest {

    private static ManagedRecord donor() {
        ManagedRecord r = new ManagedRecord(UUID.randomUUID());
        r.entityType("EVOKER");
        UUID owner = UUID.randomUUID();
        r.owner(owner);
        r.trusted().add(UUID.randomUUID());
        r.enemies().add(UUID.randomUUID());
        r.allowedTypes().add("ZOMBIE");
        r.excludedTypes().add("CREEPER");
        r.defendOwner(true);
        r.defendAllies(true);
        r.retaliate(true);
        r.attackOwnerTarget(true);
        r.pursueOutsideRange(false);
        r.clearInvalidTarget(false);
        r.friendlyFireProtect(false);
        r.profile(ManagedRecord.Profile.GUARD);
        r.aiEnabled(true);
        r.aggressive(true);
        return r;
    }

    @Test
    void copyTransfersRulesButNotAnchorOrCommand() {
        ManagedRecord from = donor();
        ManagedRecord to = new ManagedRecord(UUID.randomUUID());
        to.setAnchor(true, true, "world", 1, 2, 3, 0, 0);
        to.command(UUID.randomUUID(), 42L);
        UUID ownCommand = to.commandedTarget();
        from.command(UUID.randomUUID(), 99L);
        AllegianceService.copyAllegiance(from, to);
        assertEquals(from.owner(), to.owner());
        assertEquals(from.trusted(), to.trusted());
        assertEquals(from.enemies(), to.enemies());
        assertEquals(from.allowedTypes(), to.allowedTypes());
        assertEquals(from.excludedTypes(), to.excludedTypes());
        assertTrue(to.defendOwner());
        assertTrue(to.defendAllies());
        assertTrue(to.retaliate());
        assertTrue(to.attackOwnerTarget());
        assertFalse(to.pursueOutsideRange());
        assertFalse(to.clearInvalidTarget());
        assertFalse(to.friendlyFireProtect());
        assertEquals(ManagedRecord.Profile.GUARD, to.profile());
        assertTrue(to.aiEnabled());
        assertTrue(to.aggressive());
        // Position + fight state never transfer: the child's own anchor and
        // command survive untouched (the donor's command is NOT adopted).
        assertTrue(to.anchored());
        assertEquals("world", to.anchorWorld());
        assertEquals(ownCommand, to.commandedTarget());
        assertEquals(42L, to.commandedAt());
    }

    private static World fakeWorld(String name) {
        return (World) Proxy.newProxyInstance(InheritanceTest.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> method.getName().equals("getName") ? name : null);
    }

    @Test
    void nearestDonorPicksClosestInWorldAndType() {
        UUID near = UUID.randomUUID(), far = UUID.randomUUID(), other = UUID.randomUUID();
        List<AllegianceService.DonorCandidate> cands = List.of(
                new AllegianceService.DonorCandidate(far, "world", 15, 0, 0, "EVOKER"),
                new AllegianceService.DonorCandidate(near, "world", 3, 0, 0, "EVOKER"),
                new AllegianceService.DonorCandidate(other, "nether", 1, 0, 0, "EVOKER"));
        Location at = new Location(fakeWorld("world"), 0, 0, 0);
        assertEquals(near, AllegianceService.nearestDonor(cands, at, null));
        // Type gate: same-type required for splits.
        assertNull(AllegianceService.nearestDonor(cands, at, "SLIME"));
        assertEquals(near, AllegianceService.nearestDonor(cands, at, "EVOKER"));
        // Radius gate: nobody within 16 blocks.
        List<AllegianceService.DonorCandidate> farOnly = List.of(
                new AllegianceService.DonorCandidate(far, "world", 15, 0, 0, "EVOKER"));
        Location edge = new Location(fakeWorld("world"), 100, 0, 0);
        assertNull(AllegianceService.nearestDonor(farOnly, edge, null));
        assertNull(AllegianceService.nearestDonor(List.of(), at, null));
        assertNull(AllegianceService.nearestDonor(cands, null, null));
    }

    @Test
    void splitSnapshotPicksNearestFreshSameType() {
        World w = fakeWorld("world");
        ManagedRecord donor = donor();
        var near = new AllegianceService.SplitNote(donor,
                new Location(w, 3, 0, 0), "SLIME", 1000L);
        var far = new AllegianceService.SplitNote(donor,
                new Location(w, 15, 0, 0), "SLIME", 1000L);
        var wrongType = new AllegianceService.SplitNote(donor,
                new Location(w, 1, 0, 0), "ZOMBIE", 1000L);
        var otherWorld = new AllegianceService.SplitNote(donor,
                new Location(fakeWorld("nether"), 1, 0, 0), "SLIME", 1000L);
        var stale = new AllegianceService.SplitNote(donor,
                new Location(w, 1, 0, 0), "SLIME", 1000L);
        Location at = new Location(w, 0, 0, 0);
        List<AllegianceService.SplitNote> notes = List.of(far, wrongType, otherWorld, near);
        assertEquals(near, AllegianceService.pickSplit(notes, at, "SLIME", 2000L));
        assertNull(AllegianceService.pickSplit(notes, at, "CREEPER", 2000L));
        assertNull(AllegianceService.pickSplit(List.of(stale), at, "SLIME", 100_000L));
        assertNull(AllegianceService.pickSplit(List.of(), at, "SLIME", 2000L));
        assertNull(AllegianceService.pickSplit(notes, null, "SLIME", 2000L));
    }
}
