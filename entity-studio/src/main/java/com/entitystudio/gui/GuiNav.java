package com.entitystudio.gui;

/**
 * Pure navigation conventions for the editor GUIs. Bukkit-free and unit
 * tested so menu/session logic is covered without a live server.
 *
 * <p>54-slot pages share one bottom row so Back, pagination, Help, Refresh
 * and Close are always in the same place:
 * 45 Back · 47 Help · 48 Previous · 49 page action · 50 Next · 52 Refresh ·
 * 53 Close. Slot 49 stays page-specific (search, teleport, withdraw-all,
 * undo-last, filter, ...) and 46/51 are spare.
 *
 * <p>27-slot pages use 22 Back and 26 Close, with 0/8/25 free for
 * page-specific hints — the same idea on a smaller bar.
 */
public final class GuiNav {

    /** Content slots per 54-size page (bottom row is navigation). */
    public static final int PAGE_CONTENT = ContainerPages.PAGE_CONTENT;

    public static final int BACK_54 = 45;
    public static final int HELP_54 = 47;
    public static final int PREV_54 = 48;
    public static final int ACTION_54 = 49;
    public static final int NEXT_54 = 50;
    public static final int REFRESH_54 = 52;
    public static final int CLOSE_54 = 53;

    public static final int BACK_27 = 22;
    public static final int CLOSE_27 = 26;

    private GuiNav() {
    }

    /** True when the slot is part of the 54-size navigation bar. */
    public static boolean isNavBar54(int slot) {
        return slot >= 45 && slot < 54;
    }

    /** True when the slot holds page content (never navigation). */
    public static boolean isContent54(int slot) {
        return slot >= 0 && slot < PAGE_CONTENT;
    }

    /**
     * Whether a click type can ever mutate state. Number-key swaps, offhand
     * swaps, drops, double-click collect and drags are always ignored by the
     * GUIs (the event is already cancelled): they must never trigger a
     * button, a swap, or a deposit.
     */
    public static boolean isUnsafeClick(String clickName, boolean isDrag) {
        if (isDrag) {
            return true;
        }
        if (clickName == null) {
            return false;
        }
        return switch (clickName) {
            case "NUMBER_KEY", "SWAP_OFFHAND", "DOUBLE_CLICK", "DROP", "CONTROL_DROP",
                    "WINDOW_BORDER_LEFT", "WINDOW_BORDER_RIGHT", "UNKNOWN" -> true;
            default -> false;
        };
    }

    /**
     * Stale-menu guard: a held menu may only drive the session it was opened
     * for. Nulls never match, and string comparison is case-insensitive so
     * UUID formatting can never alias two entities.
     */
    public static boolean sameEntity(java.util.UUID holderEntity, java.util.UUID sessionEntity) {
        if (holderEntity == null || sessionEntity == null) {
            return false;
        }
        return holderEntity.equals(sessionEntity);
    }
}
