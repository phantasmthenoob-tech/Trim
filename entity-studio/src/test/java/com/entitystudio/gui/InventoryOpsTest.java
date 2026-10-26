package com.entitystudio.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryOpsTest {

    @Test
    void equipSlotMappingMatchesStudioGuiRows() {
        // Labels 10-15, items exactly +9: 19-24.
        for (int i = 0; i < 6; i++) {
            assertEquals(i, InventoryOps.equipIndexForSlot(10 + i + 9));
            assertTrue(InventoryOps.isEquipLabelSlot(10 + i));
        }
        assertEquals(-1, InventoryOps.equipIndexForSlot(10));
        assertEquals(-1, InventoryOps.equipIndexForSlot(0));
        assertEquals(-1, InventoryOps.equipIndexForSlot(45));
        assertFalse(InventoryOps.isEquipLabelSlot(19));
    }

    @Test
    void amountClampNeverDeletesOrOverstacks() {
        assertEquals(1, InventoryOps.clampAmount(0, 64));
        assertEquals(1, InventoryOps.clampAmount(-5, 64));
        assertEquals(64, InventoryOps.clampAmount(99, 64));
        assertEquals(16, InventoryOps.clampAmount(99, 16));
        assertEquals(1, InventoryOps.clampAmount(99, 1));
        assertEquals(32, InventoryOps.clampAmount(32, 64));
    }

    @Test
    void damageClampStaysInDurabilityRange() {
        assertEquals(0, InventoryOps.clampDamage(-3, 100));
        assertEquals(0, InventoryOps.clampDamage(0, 100));
        assertEquals(50, InventoryOps.clampDamage(50, 100));
        assertEquals(100, InventoryOps.clampDamage(500, 100));
        // Non-damageable (durability 0): everything clamps to 0, so callers
        // must refuse with a message instead of recording a fake edit.
        assertEquals(0, InventoryOps.clampDamage(10, 0));
    }

    @Test
    void dropChanceCyclesInQuarterSteps() {
        assertEquals(0.25f, InventoryOps.nextDropChance(0f));
        assertEquals(0.5f, InventoryOps.nextDropChance(0.25f));
        assertEquals(1.0f, InventoryOps.nextDropChance(0.75f));
        assertEquals(0f, InventoryOps.nextDropChance(1.0f));
        assertEquals(0f, InventoryOps.nextDropChance(2.0f));
    }

    @Test
    void realSlotMappingRespectsPages() {
        assertEquals(0, InventoryOps.realSlotFor(0, 27, 0));
        assertEquals(26, InventoryOps.realSlotFor(0, 27, 26));
        assertEquals(45, InventoryOps.realSlotFor(1, 100, 0));
        assertEquals(-1, InventoryOps.realSlotFor(0, 27, 45));
        assertEquals(-1, InventoryOps.realSlotFor(0, 27, -1));
        assertEquals(-1, InventoryOps.realSlotFor(0, 27, 53));
    }

    @Test
    void depositTakeNeverNegativeOrOverdrawn() {
        assertEquals(5, InventoryOps.takeFromStack(5, 10));
        assertEquals(10, InventoryOps.takeFromStack(20, 10));
        assertEquals(0, InventoryOps.takeFromStack(0, 10));
        assertEquals(0, InventoryOps.takeFromStack(-3, 10));
        assertEquals(0, InventoryOps.takeFromStack(5, 0));
    }
}
