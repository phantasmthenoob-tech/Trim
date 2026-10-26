package com.entitystudio.snapshot;

import com.entitystudio.behavior.ManagedRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotServiceTest {

    @Test
    void validEntryNamesAccepted() {
        assertTrue(SnapshotService.isValidEntryName("guard-1"));
        assertTrue(SnapshotService.isValidEntryName("My Preset_2"));
        assertTrue(SnapshotService.isValidEntryName("a"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void behaviorCaptureRoundTripsThroughYaml() {
        // behaviorData's plain-map form must survive a YAML save/load with
        // sentinels intact: __none__ owner stays a real string (explicit
        // clear), UUID lists stay lists, anchor stays a nested map.
        ManagedRecord r = new ManagedRecord(UUID.randomUUID());
        r.profile(ManagedRecord.Profile.GUARD);
        r.defendAllies(true);
        r.trusted().add(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));
        r.excludedTypes().add("CREEPER");
        r.setAnchor(true, true, "world", 1.5, 64.0, -3.25, 90.0f, 0.0f);
        Map<String, Object> data = SnapshotService.behaviorData(r);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("data", data);
        // Round-trip through text, exactly like the plugin's save/load:
        // in-memory set() keeps raw maps, but parsed files yield sections.
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yaml.saveToString()));
        ConfigurationSection root = loaded.getConfigurationSection("data");
        assertNotNull(root);
        Map<String, Object> back = (Map<String, Object>) SnapshotService.plain(root);

        assertEquals("GUARD", back.get("profile"));
        assertEquals(SnapshotPlan.NO_OWNER, back.get("owner"));
        assertEquals(List.of("01234567-89ab-cdef-0123-456789abcdef"), back.get("trusted"));
        assertEquals(List.of(), back.get("allowed-types"));
        assertEquals(List.of("CREEPER"), back.get("excluded-types"));
        assertEquals(true, back.get("defend-allies"));
        Map<String, Object> anchor = (Map<String, Object>) back.get("anchor");
        assertEquals(true, anchor.get("anchored"));
        assertEquals("world", anchor.get("world"));
        assertEquals(1.5, (Double) anchor.get("x"), 1e-9);
    }

    @Test
    void behaviorCaptureDefaultsAreExplicit() {
        // Every scalar a restore might set must be present even when the
        // record was never touched — otherwise a preset could not turn a
        // non-default flag back off (absent would mean "leave unchanged").
        Map<String, Object> data = SnapshotService.behaviorData(
                new ManagedRecord(UUID.randomUUID()));
        for (String key : List.of("profile", "ai-enabled", "owner", "trusted", "enemies",
                "allowed-types", "excluded-types", "defend-owner", "defend-allies",
                "retaliate", "attack-owner-target", "pursue-outside-range",
                "clear-invalid-target", "friendly-fire", "anchor")) {
            assertTrue(data.containsKey(key), "missing behavior key: " + key);
        }
    }

    @Test
    void hostileEntryNamesRejected() {
        assertFalse(SnapshotService.isValidEntryName(null));
        assertFalse(SnapshotService.isValidEntryName(""));
        assertFalse(SnapshotService.isValidEntryName("../managed"));
        assertFalse(SnapshotService.isValidEntryName("a/b"));
        assertFalse(SnapshotService.isValidEntryName("evil\nname"));
        assertFalse(SnapshotService.isValidEntryName("semi;colon"));
        assertFalse(SnapshotService.isValidEntryName("x".repeat(33)));
    }
}
