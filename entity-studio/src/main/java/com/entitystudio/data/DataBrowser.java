package com.entitystudio.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Pure display helpers for the inspector: tree flattening, type labels, search
 * filtering, truncation, and sensitive-key redaction. Bukkit-free and unit
 * tested — the GUI must add no logic of its own on top.
 */
public final class DataBrowser {

    /** Rows produced per flatten call at most; the GUI paginates the rest. */
    public static final int MAX_ROWS = 500;
    /** Nested levels expanded before rendering a subtree inline. */
    public static final int MAX_DEPTH = 5;
    /** Value characters shown on a GUI row. */
    public static final int DISPLAY_CHARS = 200;
    /** Value characters sent to chat on row click. */
    public static final int CHAT_CHARS = 400;

    /** PDC keys matching this have their values redacted (existence + type stay). */
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i).*(token|secret|passwd|password|api[-_ ]?key|auth|session|bearer|private[-_ ]?key).*");

    private DataBrowser() {
    }

    /** True when a key path looks credential-like and must be redacted. */
    public static boolean isSensitiveKey(String path) {
        return path != null && SENSITIVE.matcher(path).matches();
    }

    /** Redacts the value of a credential-like key in place. */
    public static DataField redactIfSensitive(DataField field) {
        if (field.readable() && isSensitiveKey(field.path())) {
            return new DataField(field.section(), field.path(), field.type(),
                    "<redacted>", DataStatus.REDACTED, field.source());
        }
        return field;
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /**
     * Flattens a nested map into stable sorted rows. Nested compounds/lists
     * past {@link #MAX_DEPTH} render inline; output stops at {@code limit}
     * rows so huge snapshots cannot flood the GUI.
     */
    public static List<DataField> flatten(Map<String, Object> map, String section, int limit) {
        List<DataField> out = new ArrayList<>();
        if (map == null) {
            return out;
        }
        int cap = Math.min(Math.max(1, limit), MAX_ROWS);
        for (Map.Entry<String, Object> e : new TreeMap<>(map).entrySet()) {
            walk(section, String.valueOf(e.getKey()), e.getValue(), 0, cap, out);
            if (out.size() >= cap) {
                break;
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(String section, String path, Object value, int depth,
            int cap, List<DataField> out) {
        if (out.size() >= cap) {
            return;
        }
        if (value instanceof Map<?, ?> m) {
            if (m.isEmpty() || depth >= MAX_DEPTH) {
                out.add(new DataField(section, path, "compound",
                        truncate(pretty(m), DISPLAY_CHARS), DataStatus.READABLE,
                        DataField.PUBLIC_API));
                return;
            }
            Map<String, Object> sorted;
            try {
                sorted = new TreeMap<>((Map<String, Object>) m);
            } catch (ClassCastException e) {
                out.add(new DataField(section, path, "compound",
                        truncate(pretty(m), DISPLAY_CHARS), DataStatus.READABLE,
                        DataField.PUBLIC_API));
                return;
            }
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                walk(section, path + "." + e.getKey(), e.getValue(), depth + 1, cap, out);
                if (out.size() >= cap) {
                    return;
                }
            }
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty() || depth >= MAX_DEPTH) {
                out.add(new DataField(section, path, "list",
                        truncate(pretty(list), DISPLAY_CHARS), DataStatus.READABLE,
                        DataField.PUBLIC_API));
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                walk(section, path + "[" + i + "]", list.get(i), depth + 1, cap, out);
                if (out.size() >= cap) {
                    return;
                }
            }
            return;
        }
        if (value instanceof SnbtParser.Scalar scalar) {
            out.add(new DataField(section, path, scalar.type(),
                    truncate(scalar.text(), DISPLAY_CHARS), DataStatus.READABLE,
                    DataField.PUBLIC_API));
            return;
        }
        out.add(new DataField(section, path, typeOf(value),
                truncate(pretty(value), DISPLAY_CHARS), DataStatus.READABLE,
                DataField.PUBLIC_API));
    }

    /** Human type label for a PDC/plain-Java value. */
    public static String typeOf(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof SnbtParser.Scalar scalar) {
            return scalar.type();
        }
        if (value instanceof Map) {
            return "compound";
        }
        if (value instanceof List) {
            return "list";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Integer) {
            return "int";
        }
        if (value instanceof Long) {
            return "long";
        }
        if (value instanceof Double) {
            return "double";
        }
        if (value instanceof Float) {
            return "float";
        }
        if (value instanceof Short) {
            return "short";
        }
        if (value instanceof Byte) {
            return "byte";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof int[]) {
            return "int[]";
        }
        if (value instanceof long[]) {
            return "long[]";
        }
        if (value instanceof byte[]) {
            return "byte[]";
        }
        if (value instanceof double[]) {
            return "double[]";
        }
        if (value instanceof float[]) {
            return "float[]";
        }
        if (value instanceof short[]) {
            return "short[]";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[]";
        }
        return value.getClass().getSimpleName();
    }

    /** Human-readable single-line rendering of any value. */
    public static String pretty(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof SnbtParser.Scalar scalar) {
            return scalar.text();
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
        if (value.getClass().isArray()) {
            return Arrays.deepToString((Object[]) value);
        }
        String s = String.valueOf(value);
        return s.length() > DISPLAY_CHARS ? s.substring(0, DISPLAY_CHARS) + "…" : s;
    }

    /** Case-insensitive substring search over section, path, type, and value. */
    public static List<DataField> filter(List<DataField> fields, String query) {
        if (query == null || query.isBlank() || fields == null) {
            return fields == null ? List.of() : List.copyOf(fields);
        }
        String q = query.toLowerCase();
        List<DataField> out = new ArrayList<>();
        for (DataField f : fields) {
            if (f.section().toLowerCase().contains(q)
                    || f.path().toLowerCase().contains(q)
                    || f.type().toLowerCase().contains(q)
                    || f.value().toLowerCase().contains(q)) {
                out.add(f);
            }
        }
        return out;
    }
}
