package com.entitystudio.gui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataRowsTest {

    @Test
    void flattensNestedMapsSorted() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("z", 1);
        inner.put("a", 2);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("b", inner);
        root.put("a", "x");
        List<DataRows.Row> rows = DataRows.flatten(root);
        assertEquals(3, rows.size());
        assertEquals("a", rows.get(0).path());
        assertEquals("b.a", rows.get(1).path());
        assertEquals("b.z", rows.get(2).path());
    }

    @Test
    void listsUseIndices() {
        Map<String, Object> root = Map.of("tags", List.of("one", "two"));
        List<DataRows.Row> rows = DataRows.flatten(root);
        assertEquals(2, rows.size());
        assertEquals("tags[0]", rows.get(0).path());
        assertEquals("one", rows.get(0).value());
        assertEquals("tags[1]", rows.get(1).path());
    }

    @Test
    void depthCapRendersInline() {
        Map<String, Object> deep = Map.of("v", 1);
        for (int i = 0; i < 10; i++) {
            deep = Map.of("n", deep);
        }
        Map<String, Object> root = Map.of("root", deep);
        List<DataRows.Row> rows = DataRows.flatten(root);
        assertFalse(rows.isEmpty());
        assertTrue(rows.size() < 10);
    }

    @Test
    void rowCapBoundsHugeMaps() {
        Map<String, Object> big = new LinkedHashMap<>();
        for (int i = 0; i < 2000; i++) {
            big.put("k" + i, i);
        }
        assertEquals(DataRows.MAX_ROWS, DataRows.flatten(big).size());
    }

    @Test
    void prettyHandlesNullsArraysAndLongStrings() {
        assertEquals("null", DataRows.pretty(null));
        assertEquals("[1, 2]", DataRows.pretty(new int[]{1, 2}));
        assertEquals("[a, b]", DataRows.pretty(List.of("a", "b")));
        String longText = "x".repeat(500);
        assertTrue(DataRows.pretty(longText).length() < 500);
    }

    @Test
    void nullMapGivesNoRows() {
        assertTrue(DataRows.flatten(null).isEmpty());
    }
}
