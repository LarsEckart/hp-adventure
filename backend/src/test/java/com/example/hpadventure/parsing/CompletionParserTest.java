package com.example.hpadventure.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionParserTest {
    @Test
    void detectsCompletionMarker() {
        assertTrue(CompletionParser.isComplete("Ende. [ABENTEUER ABGESCHLOSSEN]"));
        assertFalse(CompletionParser.isComplete("Noch nicht vorbei."));
    }
}
