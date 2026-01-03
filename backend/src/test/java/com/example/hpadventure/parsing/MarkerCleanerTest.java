package com.example.hpadventure.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkerCleanerTest {
    @Test
    void stripsMarkersAndTrims() {
        String text = "Absatz eins.\n\n[OPTION: A]\n[NEUER GEGENSTAND: X | Y]\n[ABENTEUER ABGESCHLOSSEN]\n[SZENE: Hogwarts]\n\nAbsatz zwei.";
        String cleaned = MarkerCleaner.strip(text);

        assertEquals("Absatz eins.\n\nAbsatz zwei.", cleaned);
    }
}
