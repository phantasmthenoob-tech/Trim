package com.entitystudio.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure helpers for the read-only advanced data browser. Flattens nested
 * maps/lists into stable, sorted path/value rows with a depth cap.
 */
final class DataRows {

    static final int MAX_DEPTH = 5;
    static final int MAX_ROWS = 500;

    record Row(String path, String value) {
    }

    private DataRows() {
    }

    static List<Row> flatten(Map<String, Object> map) {
        List<Row> out = new ArrayList<>();
        if (map == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : new TreeMap<>(map).entrySet()) {
            walk(String.valueOf(e.getKey()), e.getValue(), 0, out);
            if (out.size() >= MAX_ROWS) {
                break;
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(String path, Object value, int depth, List<Row> out) {
        if (out.size() >= MAX_ROWS) {
            return;
        }
        if (value instanceof Map<?, ?> m) {
            if (m.isEmpty() || depth >= MAX_DEPTH) {
                out.add(new Row(path, pretty(m)));
                return;
            }
            for (Map.Entry<?, ?> e : new TreeMap<>((Map<String, Object>) m).entrySet()) {
                walk(path + "." + e.getKey(), e.getValue(), depth + 1, out);
                if (out.size() >= MAX_ROWS) {
                    return;
                }
            }
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty() || depth >= MAX_DEPTH) {
                out.add(new Row(path, pretty(list)));
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                walk(path + "[" + i + "]", list.get(i), depth + 1, out);
                if (out.size() >= MAX_ROWS) {
                    return;
                }
            }
            return;
        }
        out.add(new Row(path, pretty(value)));
    }

    /** Human-readable, single-line rendering of any value. */
    static String pretty(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(e.getKey()).append("=").append(pretty(e.getValue()));
            }
            return sb.append("}").toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(pretty(list.get(i)));
            }
            return sb.append("]").toString();
        }
        Class<?> c = value.getClass();
        if (c.isArray()) {
            if (value instanceof byte[] a) {
                return Arrays.toString(a);
            }
            if (value instanceof int[] a) {
                return Arrays.toString(a);
            }
            if (value instanceof long[] a) {
                return Arrays.toString(a);
            }
            if (value instanceof double[] a) {
                return Arrays.toString(a);
            }
            if (value instanceof float[] a) {
                return Arrays.toString(a);
            }
            if (value instanceof short[] a) {
                return Arrays.toString(a);
            }
            return Arrays.deepToString((Object[]) value);
        }
        String s = String.valueOf(value);
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
