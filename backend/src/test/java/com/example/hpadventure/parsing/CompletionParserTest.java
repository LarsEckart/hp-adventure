package com.example.hpadventure.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionParserTest {
    @Test
    void detectsCompletionMarker() {
        CompletionParser parser = new CompletionParser();

        assertTrue(parser.isComplete("Ende. [ABENTEUER ABGESCHLOSSEN]"));
        assertFalse(parser.isComplete("Noch nicht vorbei."));
    }
}
