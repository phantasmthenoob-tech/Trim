package com.minetranslate.translate;

/** Immutable result of one translation request. */
public record TranslateResult(String text, String sourceLang, String targetLang) {
}
