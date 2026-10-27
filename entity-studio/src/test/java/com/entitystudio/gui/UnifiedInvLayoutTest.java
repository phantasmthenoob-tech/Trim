package com.entitystudio.gui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedInvLayoutTest {

    @Test
    void layoutCoversAll54SlotsExactlyOnce() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            assertTrue(seen.add(i), "equip slot " + i);
        }
        assertTrue(seen.add(UnifiedInvLayout.CRAFT_RESULT));
        for (int s : UnifiedInvLayout.CRAFT) {
            assertTrue(seen.add(s), "craft slot " + s);
        }
        for (int s : UnifiedInvLayout.CONTENT) {
            assertTrue(seen.add(s), "content slot " + s);
        }
        assertTrue(seen.add(UnifiedInvLayout.INFO_A));
        assertTrue(seen.add(UnifiedInvLayout.INFO_B));
        assertTrue(seen.add(UnifiedInvLayout.REFRESH));
        assertTrue(seen.add(UnifiedInvLayout.BACK));
        assertTrue(seen.add(UnifiedInvLayout.CLOSE));
        for (int s = 45; s < 54; s++) {
            assertTrue(seen.add(s), "hotbar slot " + s);
        }
        // Two intentional info placeholders fill the last gaps.
        assertEquals(54, seen.size(), "every GUI slot has exactly one role");
    }

    @Test
    void contentHas27Slots() {
        assertEquals(27, UnifiedInvLayout.CONTENT.length);
    }

    @Test
    void craftGridMapsToTopLeftOf3x3() {
        assertArrayEquals(new int[] { 0, 1, 3, 4 }, new int[] {
                UnifiedInvLayout.matrixSlot(0),
                UnifiedInvLayout.matrixSlot(1),
                UnifiedInvLayout.matrixSlot(2),
                UnifiedInvLayout.matrixSlot(3),
        });
        assertEquals(-1, UnifiedInvLayout.matrixSlot(9));
        for (int i = 0; i < 4; i++) {
            assertEquals(i, UnifiedInvLayout.craftPos(UnifiedInvLayout.CRAFT[i]));
        }
        assertEquals(-1, UnifiedInvLayout.craftPos(11));
    }

    @Test
    void backingOffsetRoundTrips() {
        for (int off = 0; off < UnifiedInvLayout.CONTENT_PER_PAGE; off++) {
            int gui = UnifiedInvLayout.guiSlotForOffset(off);
            assertTrue(gui >= 0, "offset " + off + " has a GUI slot");
            assertEquals(off, UnifiedInvLayout.backingOffset(gui),
                    "offset " + off + " round-trips");
        }
        // Nav/equipment/crafting slots are not storage.
        for (int s : new int[] { 0, 5, 8, 9, 42, 43, 44 }) {
            assertEquals(-1, UnifiedInvLayout.backingOffset(s), "slot " + s);
        }
    }

    @Test
    void playerMappingCoversHotbarAndMain() {
        Set<Integer> storage = new HashSet<>();
        for (int off = 0; off < UnifiedInvLayout.CONTENT_PER_PAGE; off++) {
            int slot = UnifiedInvLayout.playerStorageSlot(off);
            assertTrue(slot >= 0 && slot < 36, "player slot " + slot);
            assertTrue(storage.add(slot), "no duplicate " + slot);
        }
        assertEquals(36, storage.size());
        // Content window = main inventory, hotbar row = hotbar.
        for (int off = 0; off < 27; off++) {
            assertEquals(9 + off, UnifiedInvLayout.playerStorageSlot(off));
        }
        for (int off = 27; off < 36; off++) {
            assertEquals(off - 27, UnifiedInvLayout.playerStorageSlot(off));
        }
    }

    @Test
    void pagingMath() {
        assertEquals(1, UnifiedInvLayout.pageCount(36));
        assertEquals(2, UnifiedInvLayout.pageCount(37));
        assertEquals(1, UnifiedInvLayout.pageCount(0));
        assertEquals(0, UnifiedInvLayout.startIndex(0, 27));
        assertEquals(36, UnifiedInvLayout.startIndex(1, 40));
        assertEquals(0, UnifiedInvLayout.clampPage(9, 10));
    }

    @Test
    void equipHotbarNavClassification() {
        for (int s = 0; s <= 7; s++) {
            assertEquals(s, UnifiedInvLayout.equipIndex(s));
        }
        assertEquals(-1, UnifiedInvLayout.equipIndex(8));
        for (int s = 45; s < 54; s++) {
            assertEquals(s - 45, UnifiedInvLayout.hotbarPos(s));
        }
        assertEquals(-1, UnifiedInvLayout.hotbarPos(44));
        assertEquals(-1, UnifiedInvLayout.contentPos(44));
    }
}
