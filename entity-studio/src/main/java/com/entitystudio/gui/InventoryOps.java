package com.entitystudio.gui;

/**
 * Pure inventory/item transformation math used by the equipment editor, item
 * editor, and container mirror. Bukkit-free and unit tested — the live
 * {@code ItemStack} calls stay in {@code StudioGUI}, but every bound, wrap,
 * and slot mapping here is covered headless so GUI transitions cannot
 * silently duplicate, delete, or misroute items.
 */
public final class InventoryOps {

    /** Equipment GUI label slots, in slotStack/setSlot order. */
    public static final int[] EQUIP_LABEL_SLOTS = { 10, 11, 12, 13, 14, 15 };

    private InventoryOps() {
    }

    /**
     * Equipment index (0-5) for a GUI display slot, or -1 when the slot is
     * not an equipment item row. Item rows sit exactly 9 below their labels.
     */
    public static int equipIndexForSlot(int guiSlot) {
        for (int i = 0; i < EQUIP_LABEL_SLOTS.length; i++) {
            if (guiSlot == EQUIP_LABEL_SLOTS[i] + 9) {
                return i;
            }
        }
        return -1;
    }

    /** True when the slot is an equipment label row (drop-chance cycling). */
    public static boolean isEquipLabelSlot(int guiSlot) {
        for (int s : EQUIP_LABEL_SLOTS) {
            if (guiSlot == s) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clamps a requested stack amount into the legal range. Never returns
     * less than 1 (a zero-amount stack would delete the item) or more than
     * the item's max stack size.
     */
    public static int clampAmount(int requested, int maxStack) {
        int max = Math.max(1, maxStack);
        return Math.max(1, Math.min(max, requested));
    }

    /**
     * Clamps a requested damage value into [0, maxDurability]. Non-damageable
     * items report durability 0, which clamps everything to 0 — callers must
     * check damageability first and refuse with a message instead.
     */
    public static int clampDamage(int requested, int maxDurability) {
        return Math.max(0, Math.min(Math.max(0, maxDurability), requested));
    }

    /** Drop-chance step: +25 percentage points, wrapping past 100% to 0%. */
    public static float nextDropChance(float current) {
        if (current >= 1.0f) {
            return 0f;
        }
        return Math.round((current + 0.25f) * 100) / 100f;
    }

    /**
     * Backing-store index for a container-mirror content slot, or -1 when the
     * slot is outside the page's content range. Callers still bounds-check
     * against the live inventory size — the mirror may have gone stale.
     */
    public static int realSlotFor(int page, int pageSize, int guiSlot) {
        if (guiSlot < 0 || guiSlot >= ContainerPages.PAGE_CONTENT) {
            return -1;
        }
        return ContainerPages.startIndex(page, Math.max(1, pageSize)) + guiSlot;
    }

    /**
     * Splits a requested deposit amount across identical stacks: how much of
     * {@code moving} each same-kind stack absorbs. Pure helper so deposit
     * accounting is testable without inventories.
     */
    public static int takeFromStack(int need, int stackAmount) {
        return Math.min(Math.max(0, need), Math.max(0, stackAmount));
    }
}
