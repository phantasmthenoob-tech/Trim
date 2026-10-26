package com.entitystudio.persistence;

import com.entitystudio.behavior.ManagedRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence-format tests: round-trips, defaults parity, per-field
 * quarantine, and legacy-field tolerance. Headless — the codec is pure.
 */
class ManagedCodecTest {

    private ManagedRecord fullRecord() {
        ManagedRecord r = new ManagedRecord(UUID.randomUUID());
        r.entityType("ZOMBIE");
        r.owner(UUID.randomUUID());
        r.trusted().add(UUID.randomUUID());
        r.enemies().add(UUID.randomUUID());
        r.allowedTypes().add("SKELETON");
        r.excludedTypes().add("VILLAGER");
        r.defendOwner(true);
        r.defendAllies(true);
        r.retaliate(true);
        r.attackOwnerTarget(true);
        r.pursueOutsideRange(false);
        r.clearInvalidTarget(false);
        r.friendlyFireProtect(false);
        r.profile(ManagedRecord.Profile.GUARD);
        r.aiEnabled(false);
        r.aggressive(true);
        r.setAnchor(true, true, "world", 1.5, 64, -3.25, 90f, 0f);
        return r;
    }

    @Test
    void roundTripPreservesEverything() {
        ManagedRecord r = fullRecord();
        ManagedCodec.DecodeResult decoded =
                ManagedCodec.decode(r.entityUuid(), ManagedCodec.encode(r));
        assertTrue(decoded.clean(), String.valueOf(decoded.problems()));
        ManagedRecord back = decoded.record();
        assertEquals("ZOMBIE", back.entityType());
        assertEquals(r.owner(), back.owner());
        assertEquals(r.trusted(), back.trusted());
        assertEquals(r.enemies(), back.enemies());
        assertEquals(r.allowedTypes(), back.allowedTypes());
        assertEquals(r.excludedTypes(), back.excludedTypes());
        assertTrue(back.defendOwner());
        assertTrue(back.defendAllies());
        assertTrue(back.retaliate());
        assertTrue(back.attackOwnerTarget());
        assertFalse(back.pursueOutsideRange());
        assertFalse(back.clearInvalidTarget());
        assertFalse(back.friendlyFireProtect());
        assertEquals(ManagedRecord.Profile.GUARD, back.profile());
        assertFalse(back.aiEnabled());
        assertTrue(back.aggressive());
        assertTrue(back.anchored());
        assertTrue(back.hardAnchor());
        assertEquals("world", back.anchorWorld());
        assertEquals(1.5, back.anchorX());
    }

    @Test
    void emptyMapYieldsConservativeDefaults() {
        ManagedCodec.DecodeResult decoded =
                ManagedCodec.decode(UUID.randomUUID(), new LinkedHashMap<>());
        ManagedRecord r = decoded.record();
        assertEquals(ManagedRecord.Profile.CUSTOM, r.profile());
        assertTrue(r.aiEnabled());
        assertTrue(r.defendOwner());
        assertFalse(r.retaliate());
        assertTrue(r.friendlyFireProtect());
        assertTrue(r.pursueOutsideRange());
        assertTrue(r.clearInvalidTarget());
        assertFalse(r.anchored());
        assertNull(r.owner());
    }

    @Test
    void corruptFieldsQuarantinedIndividually() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "SKELETON");
        Map<String, Object> allegiance = new LinkedHashMap<>();
        allegiance.put("owner", "not-a-uuid");
        allegiance.put("trusted", List.of(UUID.randomUUID().toString(), "bogus"));
        allegiance.put("enemies", "should-be-a-list");
        allegiance.put("allowed-types", List.of("ZOMBIE", "not_a_type"));
        allegiance.put("defend-owner", "maybe");
        data.put("allegiance", allegiance);
        Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("profile", "BOGUS_PROFILE");
        behavior.put("anchored", true);
        behavior.put("world", "  ");
        data.put("behavior", behavior);

        ManagedCodec.DecodeResult decoded = ManagedCodec.decode(UUID.randomUUID(), data);
        assertFalse(decoded.clean());
        ManagedRecord r = decoded.record();
        // Type still loads; owner quarantined; one good trusted UUID kept.
        assertEquals("SKELETON", r.entityType());
        assertNull(r.owner());
        assertEquals(1, r.trusted().size());
        assertTrue(r.enemies().isEmpty());
        assertEquals(java.util.Set.of("ZOMBIE"), r.allowedTypes());
        assertTrue(r.defendOwner(), "non-boolean keeps default true");
        assertEquals(ManagedRecord.Profile.CUSTOM, r.profile());
        assertFalse(r.anchored(), "anchor without a world is dropped");
        assertFalse(decoded.problems().isEmpty());
    }

    @Test
    void nullEntryIsNotCleanButNeverThrows() {
        ManagedCodec.DecodeResult decoded = ManagedCodec.decode(UUID.randomUUID(), null);
        assertFalse(decoded.clean());
        assertNotNull(decoded.record());
    }

    @Test
    void unknownTopLevelFieldsAreIgnored() {
        Map<String, Object> data = new LinkedHashMap<>(ManagedCodec.encode(fullRecord()));
        data.put("future-field", "whatever");
        ManagedCodec.DecodeResult decoded =
                ManagedCodec.decode(UUID.randomUUID(), data);
        assertTrue(decoded.clean());
    }

    @Test
    void savedYamlShapeMatchesCodecKeys() {
        // Guards the ManagedStore.save/load contract: every codec section
        // key must be expressible as a dotted YAML path of plain values.
        Map<String, Object> encoded = ManagedCodec.encode(fullRecord());
        List<String> leaves = new ArrayList<>();
        flatten(encoded, "", leaves);
        assertTrue(leaves.contains("allegiance.allowed-types"));
        assertTrue(leaves.contains("allegiance.excluded-types"));
        assertTrue(leaves.contains("allegiance.pursue-outside-range"));
        assertTrue(leaves.contains("behavior.profile"));
        assertTrue(leaves.contains("type"));
    }

    @SuppressWarnings("unchecked")
    private static void flatten(Map<String, Object> map, String prefix, List<String> out) {
        for (var e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> sub) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (var se : sub.entrySet()) {
                    m.put(String.valueOf(se.getKey()), se.getValue());
                }
                flatten(m, key, out);
            } else {
                out.add(key);
            }
        }
    }
}
