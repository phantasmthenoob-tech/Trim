package com.entitystudio.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainerPagesTest {

    @Test
    void vanillaSizesFitOnePage() {
        for (int size : new int[]{0, 2, 5, 8, 9, 11, 14, 17, 27, 41, 45}) {
            assertEquals(1, ContainerPages.pageCount(size), "size " + size);
        }
    }

    @Test
    void largerSizesPaginate() {
        assertEquals(2, ContainerPages.pageCount(46));
        assertEquals(2, ContainerPages.pageCount(90));
        assertEquals(3, ContainerPages.pageCount(91));
    }

    @Test
    void pagesClamp() {
        assertEquals(0, ContainerPages.clampPage(-3, 27));
        assertEquals(0, ContainerPages.clampPage(5, 27));
        assertEquals(1, ContainerPages.clampPage(9, 90));
    }

    @Test
    void startIndexMapsGuiSlotsToBackingSlots() {
        assertEquals(0, ContainerPages.startIndex(0, 90));
        assertEquals(45, ContainerPages.startIndex(1, 90));
        assertEquals(90, ContainerPages.startIndex(2, 91));
    }

    @Test
    void navFlags() {
        assertFalse(ContainerPages.hasPrev(0, 27));
        assertFalse(ContainerPages.hasNext(0, 27));
        assertFalse(ContainerPages.hasPrev(0, 90));
        assertTrue(ContainerPages.hasNext(0, 90));
        assertTrue(ContainerPages.hasPrev(1, 90));
        assertFalse(ContainerPages.hasNext(1, 90));
    }

    @Test
    void garbagePageTagMeansZero() {
        assertEquals(0, ContainerPages.parsePage(null));
        assertEquals(0, ContainerPages.parsePage("sections:foo"));
        assertEquals(0, ContainerPages.parsePage("-2"));
        assertEquals(3, ContainerPages.parsePage("3"));
    }
}
