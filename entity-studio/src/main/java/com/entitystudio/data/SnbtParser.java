package com.entitystudio.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Minimal SNBT reader for display only. Parses the public
 * {@code Entity#getAsString()} output into nested maps, lists, and typed
 * scalar leaves ({@link Scalar}).
 *
 * <p>Diagnostic use: the tree is rendered, never written back. Strict on
 * malformed input — a bad string yields a failed {@link Result} with a reason,
 * never guessed rows. Hard caps (input length, node budget, nesting depth)
 * keep hostile or runaway snapshots from freezing the server thread.
 */
public final class SnbtParser {

    /** Snapshots longer than this are refused outright, with a reason. */
    public static final int MAX_INPUT_CHARS = 65536;
    /** Total compounds/lists/scalars parsed before giving up. */
    public static final int MAX_NODES = 4000;
    /** Nested value depth accepted. */
    public static final int MAX_DEPTH = 8;

    /** Typed scalar leaf. Type is one of string, byte, short, int, long,
     * float, double, boolean; text is the unescaped display form. */
    public record Scalar(String type, String text) {
    }

    public record Result(Map<String, Object> root, boolean ok, String error) {
    }

    private static final Pattern NUMBER =
            Pattern.compile("-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?[bBsSlLfFdD]?");

    private SnbtParser() {
    }

    public static Result parse(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            return new Result(null, false, "empty snapshot");
        }
        if (snbt.length() > MAX_INPUT_CHARS) {
            return new Result(null, false,
                    "snapshot too large (" + snbt.length() + " chars), omitted for safety");
        }
        Reader r = new Reader(snbt);
        try {
            r.ws();
            if (!r.eat('{')) {
                return new Result(null, false, "snapshot is not a compound");
            }
            Map<String, Object> root = r.compoundMembers(1);
            r.ws();
            if (!r.end()) {
                return new Result(null, false, "trailing data after root compound");
            }
            return new Result(root, true, null);
        } catch (Fail f) {
            return new Result(null, false, f.getMessage());
        }
    }

    private static final class Fail extends Exception {
        Fail(String message) {
            super(message);
        }
    }

    private static final class Reader {
        final String s;
        int p;
        int nodes;

        Reader(String s) {
            this.s = s;
        }

        boolean end() {
            return p >= s.length();
        }

        char peek() {
            return s.charAt(p);
        }

        void ws() {
            while (!end() && Character.isWhitespace(s.charAt(p))) {
                p++;
            }
        }

        boolean eat(char c) {
            if (!end() && s.charAt(p) == c) {
                p++;
                return true;
            }
            return false;
        }

        void budget() throws Fail {
            if (++nodes > MAX_NODES) {
                throw new Fail("node budget exceeded, omitted for safety");
            }
        }

        Map<String, Object> compoundMembers(int depth) throws Fail {
            if (depth > MAX_DEPTH) {
                throw new Fail("nesting too deep, omitted for safety");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            ws();
            if (eat('}')) {
                return out;
            }
            while (true) {
                budget();
                String key = key();
                ws();
                if (!eat(':')) {
                    throw new Fail("expected ':' after key '" + key + "'");
                }
                out.put(key, value(depth));
                ws();
                if (eat(',')) {
                    ws();
                    // Lenient trailing comma.
                    if (!end() && peek() == '}') {
                        p++;
                        return out;
                    }
                    continue;
                }
                if (eat('}')) {
                    return out;
                }
                throw new Fail("expected ',' or '}' in compound");
            }
        }

        Object value(int depth) throws Fail {
            if (depth > MAX_DEPTH) {
                throw new Fail("nesting too deep, omitted for safety");
            }
            budget();
            ws();
            if (end()) {
                throw new Fail("unexpected end of snapshot");
            }
            char c = peek();
            if (c == '{') {
                p++;
                return compoundMembers(depth + 1);
            }
            if (c == '[') {
                return listOrArray(depth);
            }
            if (c == '"' || c == '\'') {
                p++;
                return new Scalar("string", quoted(c));
            }
            return bare();
        }

        Object listOrArray(int depth) throws Fail {
            p++; // consume '['
            ws();
            if (eat(']')) {
                return List.of();
            }
            if (!end() && (peek() == 'B' || peek() == 'I' || peek() == 'L')
                    && p + 1 < s.length() && s.charAt(p + 1) == ';') {
                char kind = peek();
                p += 2;
                String type = kind == 'B' ? "byte[]" : kind == 'I' ? "int[]" : "long[]";
                List<String> items = new ArrayList<>();
                ws();
                if (!eat(']')) {
                    while (true) {
                        budget();
                        items.add(arrayElement());
                        ws();
                        if (eat(',')) {
                            continue;
                        }
                        if (eat(']')) {
                            break;
                        }
                        throw new Fail("expected ',' or ']' in typed array");
                    }
                }
                return new Scalar(type, "[" + String.join(", ", items) + "]");
            }
            List<Object> items = new ArrayList<>();
            while (true) {
                items.add(value(depth + 1));
                ws();
                if (eat(',')) {
                    ws();
                    // Lenient trailing comma.
                    if (!end() && peek() == ']') {
                        p++;
                        return items;
                    }
                    continue;
                }
                if (eat(']')) {
                    return items;
                }
                throw new Fail("expected ',' or ']' in list");
            }
        }

        /** Raw element text of a typed array; the array marker declares the type. */
        String arrayElement() throws Fail {
            ws();
            if (end()) {
                throw new Fail("unexpected end in typed array");
            }
            int start = p;
            while (!end() && ",]".indexOf(peek()) < 0 && !Character.isWhitespace(peek())) {
                p++;
            }
            if (start == p) {
                throw new Fail("empty element in typed array");
            }
            return s.substring(start, p);
        }

        String key() throws Fail {
            ws();
            if (end()) {
                throw new Fail("unexpected end, expected a key");
            }
            char c = peek();
            if (c == '"' || c == '\'') {
                p++;
                return quoted(c);
            }
            int start = p;
            while (!end() && isKeyChar(peek())) {
                p++;
            }
            if (start == p) {
                throw new Fail("invalid key at offset " + p);
            }
            return s.substring(start, p);
        }

        private static boolean isKeyChar(char c) {
            return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '.' || c == '_' || c == '+' || c == '-' || c == '$';
        }

        Scalar bare() throws Fail {
            int start = p;
            while (!end() && ",]}".indexOf(peek()) < 0 && !Character.isWhitespace(peek())) {
                if (peek() == '"' || peek() == '\'' || peek() == '{' || peek() == '[') {
                    break;
                }
                p++;
            }
            String token = s.substring(start, p);
            if (token.isEmpty()) {
                throw new Fail("empty value at offset " + p);
            }
            if (token.equals("true") || token.equals("false")) {
                return new Scalar("boolean", token);
            }
            if (NUMBER.matcher(token).matches()) {
                return new Scalar(numberType(token), token);
            }
            throw new Fail("unquoted value '" + token + "' is not valid SNBT");
        }

        private static String numberType(String token) {
            char last = token.charAt(token.length() - 1);
            switch (last) {
                case 'b':
                case 'B':
                    return "byte";
                case 's':
                case 'S':
                    return "short";
                case 'l':
                case 'L':
                    return "long";
                case 'f':
                case 'F':
                    return "float";
                case 'd':
                case 'D':
                    return "double";
                default:
                    return token.indexOf('.') >= 0 || token.indexOf('e') >= 0
                            || token.indexOf('E') >= 0 ? "double" : "int";
            }
        }

        String quoted(char quote) throws Fail {
            StringBuilder out = new StringBuilder();
            while (true) {
                if (end()) {
                    throw new Fail("unterminated string");
                }
                char c = s.charAt(p++);
                if (c == quote) {
                    return out.toString();
                }
                if (c == '\\') {
                    if (end()) {
                        throw new Fail("unterminated escape");
                    }
                    char e = s.charAt(p++);
                    switch (e) {
                        case 'n' -> out.append('\n');
                        case 't' -> out.append('\t');
                        case 'r' -> out.append('\r');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'u' -> out.append(unicodeEscape());
                        // Lenient: keep unknown escapes literally (display only).
                        default -> out.append(e);
                    }
                    continue;
                }
                out.append(c);
            }
        }

        char unicodeEscape() throws Fail {
            if (p + 4 > s.length()) {
                throw new Fail("bad unicode escape");
            }
            try {
                char c = (char) Integer.parseInt(s.substring(p, p + 4), 16);
                p += 4;
                return c;
            } catch (NumberFormatException e) {
                throw new Fail("bad unicode escape");
            }
        }
    }
}
