package com.example.hpadventure.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownSanitizerTest {
    @Test
    void stripsEmphasisMarkers() {
        String text = "Ein **violettes Licht** und _leise_ Stimmen.";
        String cleaned = MarkdownSanitizer.strip(text);

        assertEquals("Ein violettes Licht und leise Stimmen.", cleaned);
    }

    @Test
    void stripsCodeTicks() {
        String text = "Sag `accio` laut.";
        String cleaned = MarkdownSanitizer.strip(text);

        assertEquals("Sag accio laut.", cleaned);
    }
}
