package com.entitystudio.gui;

/**
 * Pure slot math for the unified 54-slot live inventory editor.
 * Bukkit-free and unit tested — every GUI/backing mapping here is pinned
 * headless so inventory edits can never duplicate, delete, or misroute items.
 *
 * <p>Layout (double-chest view, storage-style page — the bottom row is the
 * hotbar mirror, NOT the usual nav bar; nav lives at 42/43/44):
 * <pre>
 * Row 0: helmet chest legs boots | main off | saddle body | craft-result
 * Rows 1-3, cols 0-1: 2x2 crafting grid (9,10 / 18,19)
 * Content: 11-17, 20-26, 27-35, 36-39 (27 slots)
 * 40,41: info glass · 42 refresh · 43 back · 44 close
 * Row 5 (45-53): hotbar mirror (9 slots)
 * </pre>
 * The backing window is 36 slots per page: content offsets 0-26, then
 * hotbar-row offsets 27-35. Players map content to storage 9-35 and the
 * hotbar row to storage 0-8; native/stash backings map offsets directly
 * (horses skip their saddle+armor slots, which are shown in the top row).
 */
public final class UnifiedInvLayout {

    public static final int SIZE = 54;

    public static final int HELMET = 0;
    public static final int CHEST = 1;
    public static final int LEGS = 2;
    public static final int BOOTS = 3;
    public static final int MAIN_HAND = 4;
    public static final int OFF_HAND = 5;
    public static final int SADDLE = 6;
    public static final int BODY = 7;
    public static final int CRAFT_RESULT = 8;

    /** Crafting grid GUI slots, in grid positions 0-3 (row-major 2x2). */
    public static final int[] CRAFT = { 9, 10, 18, 19 };

    /** Content display slots, in backing-offset order 0-26. */
    public static final int[] CONTENT = {
            11, 12, 13, 14, 15, 16, 17,
            20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39,
    };

    public static final int INFO_A = 40;
    public static final int INFO_B = 41;
    public static final int REFRESH = 42;
    public static final int BACK = 43;
    public static final int CLOSE = 44;
    public static final int HOTBAR_START = 45;

    /** Backing slots per page: 27 content + 9 hotbar-row. */
    public static final int CONTENT_PER_PAGE = 36;

    private UnifiedInvLayout() {
    }

    /** Equipment-row slot (0-7) or -1. */
    public static int equipIndex(int guiSlot) {
        return guiSlot >= 0 && guiSlot <= 7 ? guiSlot : -1;
    }

    /** Crafting-grid position (0-3) or -1. */
    public static int craftPos(int guiSlot) {
        for (int i = 0; i < CRAFT.length; i++) {
            if (CRAFT[i] == guiSlot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 3x3 crafting-matrix index for a 2x2 grid position. The 2x2 grid sits
     * in the matrix's top-left corner (positions 0,1 / 3,4).
     */
    public static int matrixSlot(int craftPos) {
        return switch (craftPos) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 4;
            default -> -1;
        };
    }

    /** Content position (0-26) for a GUI slot, or -1. */
    public static int contentPos(int guiSlot) {
        for (int i = 0; i < CONTENT.length; i++) {
            if (CONTENT[i] == guiSlot) {
                return i;
            }
        }
        return -1;
    }

    /** Hotbar-row position (0-8) for a GUI slot, or -1. */
    public static int hotbarPos(int guiSlot) {
        if (guiSlot >= HOTBAR_START && guiSlot < SIZE) {
            return guiSlot - HOTBAR_START;
        }
        return -1;
    }

    /**
     * Backing-window offset (0-35) for a GUI slot, or -1 for non-storage
     * slots (equipment, crafting, nav, info). Content positions map to
     * 0-26, hotbar-row positions to 27-35.
     */
    public static int backingOffset(int guiSlot) {
        int c = contentPos(guiSlot);
        if (c >= 0) {
            return c;
        }
        int h = hotbarPos(guiSlot);
        if (h >= 0) {
            return 27 + h;
        }
        return -1;
    }

    /** GUI slot for a backing-window offset (0-35), or -1. */
    public static int guiSlotForOffset(int offset) {
        if (offset >= 0 && offset < CONTENT.length) {
            return CONTENT[offset];
        }
        if (offset >= 27 && offset < CONTENT_PER_PAGE) {
            return HOTBAR_START + (offset - 27);
        }
        return -1;
    }

    /**
     * Player storage index for a backing-window offset: content maps to
     * main inventory (9-35), the hotbar row maps to hotbar (0-8).
     */
    public static int playerStorageSlot(int offset) {
        if (offset >= 0 && offset < 27) {
            return 9 + offset;
        }
        if (offset >= 27 && offset < CONTENT_PER_PAGE) {
            return offset - 27;
        }
        return -1;
    }

    public static int pageCount(int backingSize) {
        return Math.max(1, (Math.max(0, backingSize) + CONTENT_PER_PAGE - 1) / CONTENT_PER_PAGE);
    }

    public static int clampPage(int page, int backingSize) {
        return Math.max(0, Math.min(page, pageCount(backingSize) - 1));
    }

    /** First backing slot shown on the page (before kind-specific biases). */
    public static int startIndex(int page, int backingSize) {
        return clampPage(page, backingSize) * CONTENT_PER_PAGE;
    }
}
