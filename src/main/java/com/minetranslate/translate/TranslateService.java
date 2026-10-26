package com.minetranslate.translate;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Translates text via Google's free gtx endpoint (no API key required).
 * Thread-safe, with a small in-memory LRU cache and chunking for long text.
 */
public class TranslateService {

    private static final String ENDPOINT = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=%s&tl=%s&dt=t&q=%s";

    private final HttpClient http;
    private final Executor executor;
    private final int timeoutSeconds;
    private final int maxChunk;
    private final Map<String, CacheEntry> cache;

    public TranslateService(int timeoutSeconds, int maxChunk, int cacheSize, long cacheExpireMinutes) {
        this.timeoutSeconds = Math.max(3, timeoutSeconds);
        this.maxChunk = Math.max(500, maxChunk);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "MineTranslate-http");
            t.setDaemon(true);
            return t;
        });
        final long expireMillis = cacheExpireMinutes <= 0 ? Long.MAX_VALUE
                : cacheExpireMinutes * 60_000L;
        final int cap = Math.max(50, cacheSize);
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(cap, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > cap;
            }
        });
        this.expireMillis = expireMillis;
    }

    private final long expireMillis;

    private record CacheEntry(TranslateResult result, long storedAt) {
    }

    public CompletableFuture<TranslateResult> translate(String text, String sourceLang, String targetLang) {
        String src = (sourceLang == null || sourceLang.isBlank() || sourceLang.equalsIgnoreCase("auto"))
                ? "auto" : sourceLang;
        String dst = targetLang == null ? "en" : targetLang;
        String key = src.toLowerCase(Locale.ROOT) + "|" + dst.toLowerCase(Locale.ROOT) + "|" + text;
        CacheEntry hit = cache.get(key);
        if (hit != null && (Instant.now().toEpochMilli() - hit.storedAt()) < expireMillis) {
            return CompletableFuture.completedFuture(hit.result());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                TranslateResult result = translateBlocking(text, src, dst);
                cache.put(key, new CacheEntry(result, Instant.now().toEpochMilli()));
                return result;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException("Translation failed: " + e.getMessage(), e);
            }
        }, executor);
    }

    private TranslateResult translateBlocking(String text, String src, String dst)
            throws IOException, InterruptedException {
        List<String> chunks = split(text, maxChunk);
        StringBuilder out = new StringBuilder();
        String detected = src.equals("auto") ? "auto" : src;
        for (String chunk : chunks) {
            String url = String.format(ENDPOINT,
                    URLEncoder.encode(src, StandardCharsets.UTF_8),
                    URLEncoder.encode(dst, StandardCharsets.UTF_8),
                    URLEncoder.encode(chunk, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "Mozilla/5.0 (MineTranslate Paper plugin)")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) {
                throw new IOException("Google returned HTTP " + res.statusCode());
            }
            Parsed parsed = parseGoogleResponse(res.body());
            out.append(parsed.text());
            if (detected.equals("auto") && parsed.detectedSource() != null) {
                detected = parsed.detectedSource();
            }
        }
        return new TranslateResult(out.toString(), detected, dst);
    }

    /** Splits long text on whitespace boundaries so no chunk exceeds max. */
    static List<String> split(String text, int max) {
        if (text.length() <= max) {
            return List.of(text);
        }
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + max, text.length());
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > start + max / 2) {
                    end = space;
                }
            }
            out.add(text.substring(start, end));
            start = end;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
        return out;
    }

    record Parsed(String text, String detectedSource) {
    }

    /**
     * Parses the gtx JSON response without a JSON library.
     * Format: [[["translated","original",...],...],null,"detectedLang",...]
     *
     * <p>Only the FIRST string of each direct child of the sentences array is
     * translated text. Everything nested deeper (alternative translations,
     * confidence hashes, language lists) is metadata and must be ignored.
     */
    static Parsed parseGoogleResponse(String json) throws IOException {
        if (json == null || json.isBlank() || json.charAt(0) != '[') {
            throw new IOException("Bad translation response");
        }
        // Locate the sentences array: first element of the top-level array.
        int sentencesStart = skipWs(json, 1);
        if (sentencesStart >= json.length() || json.charAt(sentencesStart) != '[') {
            throw new IOException("Could not parse translation response");
        }
        int sentencesEnd = matchBracket(json, sentencesStart);
        // Each direct child ["translated", ...] contributes its first string.
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int x = sentencesStart + 1; x < sentencesEnd; x++) {
            char c = json.charAt(x);
            if (c == '"' && depth == 1) {
                // A string directly inside a sentence array at depth 1 could be
                // either the translated text (first element) or the original
                // (second). Only take it if it IS the first element.
                int back = skipWsBack(json, x - 1, sentencesStart + 1);
                if (json.charAt(back) == '[') {
                    sb.append(readQuoted(json, x));
                }
                x = skipQuoted(json, x) - 1;
            } else if (c == '"') {
                x = skipQuoted(json, x) - 1;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            }
        }
        if (sb.length() == 0) {
            throw new IOException("Could not parse translation response");
        }
        String detected = findTopLevelLang(json);
        return new Parsed(sb.toString(), detected);
    }

    /** Index of the ']' matching the '[' at {@code openAt} (strings respected). */
    private static int matchBracket(String s, int openAt) throws IOException {
        int depth = 0;
        int n = s.length();
        for (int x = openAt; x < n; x++) {
            char c = s.charAt(x);
            if (c == '"') {
                x = skipQuoted(s, x) - 1;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return x;
                }
            }
        }
        throw new IOException("Could not parse translation response");
    }

    private static int skipWs(String s, int from) {
        while (from < s.length() && Character.isWhitespace(s.charAt(from))) {
            from++;
        }
        return from;
    }

    private static int skipWsBack(String s, int from, int floor) {
        while (from > floor && Character.isWhitespace(s.charAt(from))) {
            from--;
        }
        return from;
    }

    private static String findTopLevelLang(String json) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        int n = json.length();
        // skip the first element (sentences array) then look for first short
        // string at depth 1.
        boolean pastFirst = false;
        int firstEnd = -1;
        // Find end of first top-level element by bracket matching.
        int d = 0;
        boolean s = false;
        boolean e = false;
        for (int x = 0; x < n; x++) {
            char c = json.charAt(x);
            if (s) {
                if (e) {
                    e = false;
                } else if (c == '\\') {
                    e = true;
                } else if (c == '"') {
                    s = false;
                }
                continue;
            }
            if (c == '"') {
                s = true;
            } else if (c == '[') {
                d++;
            } else if (c == ']') {
                d--;
                if (d == 1) {
                    // closed first element? depth back to 1 means first element done
                    // only the very first time
                    if (!pastFirst) {
                        pastFirst = true;
                        firstEnd = x;
                        break;
                    }
                }
            }
        }
        if (firstEnd < 0) {
            return null;
        }
        // scan remainder for strings at depth 1
        depth = 1;
        inStr = false;
        esc = false;
        for (int x = firstEnd + 1; x < n; x++) {
            char c = json.charAt(x);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                if (depth == 1) {
                    String val = readQuoted(json, x);
                    if (val != null && val.matches("[A-Za-z-]{2,8}")) {
                        return val;
                    }
                    x = skipQuoted(json, x) - 1;
                } else {
                    inStr = true;
                }
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            }
        }
        return null;
    }

    private static String readQuoted(String s, int quoteAt) {
        StringBuilder sb = new StringBuilder();
        int n = s.length();
        for (int x = quoteAt + 1; x < n; x++) {
            char c = s.charAt(x);
            if (c == '\\' && x + 1 < n) {
                char nx = s.charAt(x + 1);
                switch (nx) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (x + 5 < n) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(x + 2, x + 6), 16));
                                x += 4;
                            } catch (NumberFormatException ex) {
                                sb.append(nx);
                            }
                        }
                        break;
                    }
                    default -> sb.append(nx);
                }
                x++;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int skipQuoted(String s, int quoteAt) {
        int n = s.length();
        for (int x = quoteAt + 1; x < n; x++) {
            char c = s.charAt(x);
            if (c == '\\') {
                x++;
            } else if (c == '"') {
                return x + 1;
            }
        }
        return n;
    }
}
