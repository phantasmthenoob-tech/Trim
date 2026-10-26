package com.entitystudio.snapshot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotServiceTest {

    @Test
    void validEntryNamesAccepted() {
        assertTrue(SnapshotService.isValidEntryName("guard-1"));
        assertTrue(SnapshotService.isValidEntryName("My Preset_2"));
        assertTrue(SnapshotService.isValidEntryName("a"));
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
