package com.minetranslate.translate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of language codes supported by Google Translate.
 * Codes follow ISO-639-1 plus Google extras (zh-CN, zh-TW, etc).
 */
public final class Languages {

    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        register("af", "Afrikaans");
        register("sq", "Albanian");
        register("am", "Amharic");
        register("ar", "Arabic");
        register("hy", "Armenian");
        register("az", "Azerbaijani");
        register("eu", "Basque");
        register("be", "Belarusian");
        register("bn", "Bengali");
        register("bs", "Bosnian");
        register("bg", "Bulgarian");
        register("ca", "Catalan");
        register("ceb", "Cebuano");
        register("ny", "Chichewa");
        register("zh-CN", "Chinese (Simplified)");
        register("zh-TW", "Chinese (Traditional)");
        register("co", "Corsican");
        register("hr", "Croatian");
        register("cs", "Czech");
        register("da", "Danish");
        register("nl", "Dutch");
        register("en", "English");
        register("eo", "Esperanto");
        register("et", "Estonian");
        register("tl", "Filipino");
        register("fi", "Finnish");
        register("fr", "French");
        register("fy", "Frisian");
        register("gl", "Galician");
        register("ka", "Georgian");
        register("de", "German");
        register("el", "Greek");
        register("gu", "Gujarati");
        register("ht", "Haitian Creole");
        register("ha", "Hausa");
        register("haw", "Hawaiian");
        register("iw", "Hebrew");
        register("he", "Hebrew");
        register("hi", "Hindi");
        register("hmn", "Hmong");
        register("hu", "Hungarian");
        register("is", "Icelandic");
        register("ig", "Igbo");
        register("id", "Indonesian");
        register("ga", "Irish");
        register("it", "Italian");
        register("ja", "Japanese");
        register("jw", "Javanese");
        register("kn", "Kannada");
        register("kk", "Kazakh");
        register("km", "Khmer");
        register("ko", "Korean");
        register("ku", "Kurdish (Kurmanji)");
        register("ky", "Kyrgyz");
        register("lo", "Lao");
        register("la", "Latin");
        register("lv", "Latvian");
        register("lt", "Lithuanian");
        register("lb", "Luxembourgish");
        register("mk", "Macedonian");
        register("mg", "Malagasy");
        register("ms", "Malay");
        register("ml", "Malayalam");
        register("mt", "Maltese");
        register("mi", "Maori");
        register("mr", "Marathi");
        register("mn", "Mongolian");
        register("my", "Myanmar (Burmese)");
        register("ne", "Nepali");
        register("no", "Norwegian");
        register("or", "Odia");
        register("ps", "Pashto");
        register("fa", "Persian");
        register("pl", "Polish");
        register("pt", "Portuguese");
        register("pa", "Punjabi");
        register("ro", "Romanian");
        register("ru", "Russian");
        register("sm", "Samoan");
        register("gd", "Scots Gaelic");
        register("sr", "Serbian");
        register("st", "Sesotho");
        register("sn", "Shona");
        register("sd", "Sindhi");
        register("si", "Sinhala");
        register("sk", "Slovak");
        register("sl", "Slovenian");
        register("so", "Somali");
        register("es", "Spanish");
        register("su", "Sundanese");
        register("sw", "Swahili");
        register("sv", "Swedish");
        register("tg", "Tajik");
        register("ta", "Tamil");
        register("te", "Telugu");
        register("th", "Thai");
        register("tr", "Turkish");
        register("uk", "Ukrainian");
        register("ur", "Urdu");
        register("ug", "Uyghur");
        register("uz", "Uzbek");
        register("vi", "Vietnamese");
        register("cy", "Welsh");
        register("xh", "Xhosa");
        register("yi", "Yiddish");
        register("yo", "Yoruba");
        register("zu", "Zulu");
    }

    private static void register(String code, String name) {
        NAMES.put(code.toLowerCase(Locale.ROOT), name);
        if (code.contains("-")) {
            // allow "zh-cn" style lookups too
            NAMES.putIfAbsent(code.replace("-", "").toLowerCase(Locale.ROOT), name);
        }
    }

    private Languages() {
    }

    /** Normalises user input to a canonical code, or null if unsupported. */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String key = input.trim().toLowerCase(Locale.ROOT).replace("_", "-");
        if (key.equals("zh")) {
            return "zh-CN";
        }
        if (key.equals("zhcn")) {
            return "zh-CN";
        }
        if (key.equals("zhtw")) {
            return "zh-TW";
        }
        if (key.equals("iw")) {
            return "he";
        }
        // direct hit (canonical case preserved for zh variants)
        for (String code : NAMES.keySet()) {
            if (code.equals(key)) {
                if (code.equals("zh-cn")) {
                    return "zh-CN";
                }
                if (code.equals("zh-tw")) {
                    return "zh-TW";
                }
                return code;
            }
        }
        return null;
    }

    public static boolean isSupported(String input) {
        return normalize(input) != null;
    }

    /** Display/canonical form of a stored code (fixes zh casing). */
    public static String canonical(String code) {
        if (code == null) {
            return null;
        }
        if (code.equalsIgnoreCase("zh-cn")) {
            return "zh-CN";
        }
        if (code.equalsIgnoreCase("zh-tw")) {
            return "zh-TW";
        }
        return code;
    }

    public static String nameOf(String code) {
        if (code == null) {
            return "?";
        }
        String n = NAMES.get(code.toLowerCase(Locale.ROOT));
        if (n != null) {
            return n;
        }
        String norm = normalize(code);
        if (norm != null) {
            n = NAMES.get(norm.toLowerCase(Locale.ROOT));
            if (n != null) {
                return n;
            }
            return norm;
        }
        return code;
    }

    public static Map<String, String> all() {
        return Collections.unmodifiableMap(NAMES);
    }

    public static List<String> codes() {
        return new ArrayList<>(NAMES.keySet());
    }

    public static List<String> search(String query) {
        if (query == null || query.isBlank()) {
            return codes();
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : NAMES.entrySet()) {
            if (e.getKey().contains(q) || e.getValue().toLowerCase(Locale.ROOT).contains(q)) {
                // de-dupe zhcn/zhtw style aliases
                if ((e.getKey().equals("zhcn") || e.getKey().equals("zhtw"))) {
                    continue;
                }
                out.add(e.getKey());
            }
        }
        return out;
    }
}
