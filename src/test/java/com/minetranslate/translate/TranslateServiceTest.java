package com.minetranslate.translate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TranslateServiceTest {

    @Test
    void parsesSimpleResponse() throws Exception {
        String json = "[[[\"Hola mundo\",\"Hello world\",null,null,10]],null,\"en\",null,null,null,[],[]]";
        TranslateService.Parsed p = TranslateService.parseGoogleResponse(json);
        assertEquals("Hola mundo", p.text());
        assertEquals("en", p.detectedSource());
    }

    @Test
    void parsesMultiSentenceResponse() throws Exception {
        String json = "[[[\"Hola\",\"Hello\",null,null,3],[\" mundo\",\" world\",null,null,4]],null,\"en\"]";
        TranslateService.Parsed p = TranslateService.parseGoogleResponse(json);
        assertEquals("Hola mundo", p.text());
        assertEquals("en", p.detectedSource());
    }

    @Test
    void ignoresTrailingMetadataHashes() throws Exception {
        // Real-world shape: confidence/hash metadata after the sentences block.
        String json = "[[[\"Hola mundo\",\"Hello world\",null,null,10]],null,\"en\",null,null,null,0.76,[],[[\"en\"],null,[0.76],[\"en\"]]]";
        TranslateService.Parsed p = TranslateService.parseGoogleResponse(json);
        assertEquals("Hola mundo", p.text());
        assertEquals("en", p.detectedSource());
    }

    @Test
    void ignoresNestedAlternativeTranslations() throws Exception {
        String json = "[[[\"Bonjour\",\"Hello\",null,null,10,null,null,[],[],[[\"Bonjour\",\"Hello\",null,null,1]]]],null,\"en\"]";
        TranslateService.Parsed p = TranslateService.parseGoogleResponse(json);
        assertEquals("Bonjour", p.text());
    }

    @Test
    void parsesEscapedQuotes() throws Exception {
        String json = "[[[\"Er sagte \\\"Hallo\\\"\",\"He said \\\"hi\\\"\",null,null,10]],null,\"de\"]";
        TranslateService.Parsed p = TranslateService.parseGoogleResponse(json);
        assertEquals("Er sagte \"Hallo\"", p.text());
    }

    @Test
    void splitsLongText() {
        String text = "word ".repeat(500); // 2500 chars
        List<String> chunks = TranslateService.split(text, 1500);
        assertTrue(chunks.size() >= 2);
        for (String c : chunks) {
            assertTrue(c.length() <= 1500);
        }
        assertEquals(text.trim(), String.join(" ", chunks).trim().replaceAll("\\s+", " "));
    }

    @Test
    void languagesNormalize() {
        assertEquals("es", Languages.normalize("es"));
        assertEquals("es", Languages.normalize("ES"));
        assertEquals("zh-CN", Languages.normalize("zh-CN"));
        assertEquals("zh-CN", Languages.normalize("zh"));
        assertEquals("he", Languages.normalize("iw"));
        assertNull(Languages.normalize("xx-not-a-lang"));
        assertTrue(Languages.isSupported("fr"));
    }
}
