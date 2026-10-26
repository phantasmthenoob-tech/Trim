package com.entitystudio.data;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataBrowserTest {

    @Test
    void flattensNestedMapsSortedWithTypes() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("z", 1);
        inner.put("a", new SnbtParser.Scalar("float", "2.5f"));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("b", inner);
        root.put("a", "x");
        List<DataField> rows = DataBrowser.flatten(root, "snbt", 100);
        assertEquals(3, rows.size());
        assertEquals("a", rows.get(0).path());
        assertEquals("string", rows.get(0).type());
        assertEquals("b.a", rows.get(1).path());
        assertEquals("float", rows.get(1).type());
        assertEquals("2.5f", rows.get(1).value());
        assertEquals("b.z", rows.get(2).path());
        assertEquals("int", rows.get(2).type());
        assertTrue(rows.stream().allMatch(f -> f.source().equals(DataField.PUBLIC_API)));
    }

    @Test
    void listsUseIndicesAndCapsApply() {
        Map<String, Object> root = Map.of("tags", List.of("one", "two"));
        List<DataField> rows = DataBrowser.flatten(root, "tags", 100);
        assertEquals(2, rows.size());
        assertEquals("tags[0]", rows.get(0).path());
        assertEquals("one", rows.get(0).value());

        Map<String, Object> deep = Map.of("v", 1);
        for (int i = 0; i < 10; i++) {
            deep = Map.of("n", deep);
        }
        List<DataField> capped = DataBrowser.flatten(Map.of("root", deep), "snbt", 100);
        assertFalse(capped.isEmpty());
        assertTrue(capped.size() < 10);
        assertEquals("compound", capped.get(0).type());

        Map<String, Object> big = new LinkedHashMap<>();
        for (int i = 0; i < 2000; i++) {
            big.put("k" + i, i);
        }
        assertEquals(DataBrowser.MAX_ROWS, DataBrowser.flatten(big, "pdc", 5000).size());
        assertTrue(DataBrowser.flatten(null, "pdc", 10).isEmpty());
    }

    @Test
    void prettyHandlesNullsArraysAndLongStrings() {
        assertEquals("null", DataBrowser.pretty(null));
        assertEquals("[1, 2]", DataBrowser.pretty(new int[]{1, 2}));
        assertEquals("[a, b]", DataBrowser.pretty(List.of("a", "b")));
        assertEquals("text", DataBrowser.pretty(new SnbtParser.Scalar("string", "text")));
        String longText = "x".repeat(500);
        assertTrue(DataBrowser.pretty(longText).length() < 500);
        assertEquals("int", DataBrowser.typeOf(3));
        assertEquals("long", DataBrowser.typeOf(3L));
        assertEquals("byte[]", DataBrowser.typeOf(new byte[]{1}));
        assertEquals("null", DataBrowser.typeOf(null));
    }

    @Test
    void filterMatchesPathTypeValueAndSection() {
        List<DataField> rows = List.of(
                new DataField("snbt", "snbt.Health", "float", "20.0f",
                        DataStatus.READABLE, DataField.PUBLIC_API),
                new DataField("pdc", "pdc.my:claims", "string", "hello",
                        DataStatus.READABLE, DataField.PUBLIC_API));
        assertEquals(2, DataBrowser.filter(rows, null).size());
        assertEquals(2, DataBrowser.filter(rows, "").size());
        assertEquals(1, DataBrowser.filter(rows, "health").size());
        assertEquals(1, DataBrowser.filter(rows, "FLOAT").size());
        assertEquals(1, DataBrowser.filter(rows, "hello").size());
        assertEquals(1, DataBrowser.filter(rows, "pdc").size());
        assertTrue(DataBrowser.filter(rows, "zzz").isEmpty());
    }

    @Test
    void sensitiveKeysAreRedacted() {
        assertTrue(DataBrowser.isSensitiveKey("pdc.my:api_key"));
        assertTrue(DataBrowser.isSensitiveKey("pdc.other:AUTH_TOKEN"));
        assertTrue(DataBrowser.isSensitiveKey("pdc.x:session-data"));
        assertTrue(DataBrowser.isSensitiveKey("pdc.x:client_secret"));
        assertFalse(DataBrowser.isSensitiveKey("pdc.my:display_name"));
        assertFalse(DataBrowser.isSensitiveKey("snbt.CustomName"));

        DataField secret = new DataField("pdc", "pdc.my:api_key", "string", "hunter2",
                DataStatus.READABLE, DataField.PUBLIC_API);
        DataField redacted = DataBrowser.redactIfSensitive(secret);
        assertEquals(DataStatus.REDACTED, redacted.status());
        assertEquals("<redacted>", redacted.value());
        assertEquals("string", redacted.type());
        assertEquals("pdc.my:api_key", redacted.path());

        DataField plain = new DataField("snbt", "snbt.Health", "float", "20.0f",
                DataStatus.READABLE, DataField.PUBLIC_API);
        assertSame(plain, DataBrowser.redactIfSensitive(plain));
    }
}
