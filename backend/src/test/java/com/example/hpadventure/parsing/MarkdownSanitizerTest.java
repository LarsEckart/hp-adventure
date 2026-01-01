package com.example.hpadventure.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownSanitizerTest {
    @Test
    void stripsEmphasisMarkers() {
        MarkdownSanitizer sanitizer = new MarkdownSanitizer();

        String text = "Ein **violettes Licht** und _leise_ Stimmen.";
        String cleaned = sanitizer.strip(text);

        assertEquals("Ein violettes Licht und leise Stimmen.", cleaned);
    }

    @Test
    void stripsCodeTicks() {
        MarkdownSanitizer sanitizer = new MarkdownSanitizer();

        String text = "Sag `accio` laut.";
        String cleaned = sanitizer.strip(text);

        assertEquals("Sag accio laut.", cleaned);
    }
}
