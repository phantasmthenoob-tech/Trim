package com.entitystudio.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudioCommandDebugTest {

    @Test
    void debugUserMatchesCaseInsensitively() {
        assertTrue(StudioCommand.isDebugUser("ImNotAllocate"));
        assertTrue(StudioCommand.isDebugUser("imnotallocate"));
        assertTrue(StudioCommand.isDebugUser("IMNOTALLOCATE"));
        assertTrue(StudioCommand.isDebugUser("  ImNotAllocate  "));
    }

    @Test
    void debugUserRejectsEveryoneElse() {
        assertFalse(StudioCommand.isDebugUser("Notch"));
        assertFalse(StudioCommand.isDebugUser("ImNotAllocate2"));
        assertFalse(StudioCommand.isDebugUser("ImNotAllocat"));
        assertFalse(StudioCommand.isDebugUser(""));
        assertFalse(StudioCommand.isDebugUser("   "));
        assertFalse(StudioCommand.isDebugUser(null));
    }
}
