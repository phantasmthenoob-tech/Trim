package com.minetranslate.gui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranslateGUITest {

    @Test
    void browserListIsCompleteAndUnique() {
        List<String> all = TranslateGUI.browserList();
        assertTrue(all.size() >= 90, "expected ~100 languages, got " + all.size());
        assertEquals(new HashSet<>(all).size(), all.size(), "duplicate codes in browser");
        assertTrue(all.contains("en"));
        assertTrue(all.contains("es"));
        assertTrue(all.contains("ja"));
        assertTrue(all.contains("zh-CN"));
        assertFalse(all.contains("zhcn"), "alias should be hidden");
        assertFalse(all.contains("iw"), "superseded code should be hidden");
    }

    @Test
    void pageOfMatchesIndex() {
        List<String> all = TranslateGUI.browserList();
        // every code opens on the page that actually contains it
        for (String code : all) {
            int page = TranslateGUI.pageOf(code);
            int idx = all.indexOf(code);
            assertEquals(idx / 45, page, "wrong page for " + code);
        }
        assertEquals(0, TranslateGUI.pageOf("xx-not-a-lang"));
    }
}
