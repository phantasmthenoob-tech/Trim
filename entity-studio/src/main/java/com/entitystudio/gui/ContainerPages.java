package com.entitystudio.gui;

/**
 * Pure pagination math for the size-safe inventory mirror. Any backing size
 * works: vanilla sizes (2, 5, 8, 11, 14, 17, 27, 41) fit one page; larger
 * ones spill onto more pages instead of crashing the client view.
 */
final class ContainerPages {

    /** Content slots per page (bottom row is navigation). */
    static final int PAGE_CONTENT = 45;

    private ContainerPages() {
    }

    static int pageCount(int size) {
        return Math.max(1, (Math.max(0, size) + PAGE_CONTENT - 1) / PAGE_CONTENT);
    }

    static int clampPage(int page, int size) {
        return Math.max(0, Math.min(page, pageCount(size) - 1));
    }

    /** Backing slot index of the first content slot on the page. */
    static int startIndex(int page, int size) {
        return clampPage(page, size) * PAGE_CONTENT;
    }

    static boolean hasPrev(int page, int size) {
        return clampPage(page, size) > 0;
    }

    static boolean hasNext(int page, int size) {
        return clampPage(page, size) < pageCount(size) - 1;
    }

    /** Parses the holder page tag; garbage means page 0. */
    static int parsePage(String data) {
        if (data == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(data));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
