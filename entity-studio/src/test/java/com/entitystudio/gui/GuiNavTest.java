package com.entitystudio.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GuiNavTest {

    @Test
    void navBarSlotsStable() {
        assertEquals(45, GuiNav.BACK_54);
        assertEquals(53, GuiNav.CLOSE_54);
        assertEquals(52, GuiNav.REFRESH_54);
        assertTrue(GuiNav.isNavBar54(45));
        assertTrue(GuiNav.isNavBar54(53));
        assertTrue(GuiNav.isContent54(0));
        assertTrue(GuiNav.isContent54(44));
        assertFalse(GuiNav.isContent54(45));
        assertFalse(GuiNav.isNavBar54(44));
    }

    @Test
    void unsafeClicksNeverMutate() {
        assertTrue(GuiNav.isUnsafeClick("NUMBER_KEY", false));
        assertTrue(GuiNav.isUnsafeClick("SWAP_OFFHAND", false));
        assertTrue(GuiNav.isUnsafeClick("DOUBLE_CLICK", false));
        assertTrue(GuiNav.isUnsafeClick("DROP", false));
        assertTrue(GuiNav.isUnsafeClick("CONTROL_DROP", false));
        assertTrue(GuiNav.isUnsafeClick("LEFT", true));
        assertFalse(GuiNav.isUnsafeClick("LEFT", false));
        assertFalse(GuiNav.isUnsafeClick("SHIFT_LEFT", false));
        assertFalse(GuiNav.isUnsafeClick("RIGHT", false));
    }

    @Test
    void staleMenuGuard() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(GuiNav.sameEntity(a, a));
        assertFalse(GuiNav.sameEntity(a, b));
        assertFalse(GuiNav.sameEntity(null, a));
        assertFalse(GuiNav.sameEntity(a, null));
    }

    @Test
    void paginationMatchesContainerPages() {
        assertEquals(ContainerPages.pageCount(0), 1);
        assertEquals(ContainerPages.pageCount(45), 1);
        assertEquals(ContainerPages.pageCount(46), 2);
        assertEquals(ContainerPages.clampPage(9, 10), 0);
        assertEquals(ContainerPages.startIndex(1, 50), 45);
    }
}
