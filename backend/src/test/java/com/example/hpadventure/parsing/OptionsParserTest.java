package com.example.hpadventure.parsing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionsParserTest {
    @Test
    void parsesOptions() {
        String text = "Was tust du?\n[OPTION: Leise gehen]\n[OPTION: Zauber wirken]\n[OPTION: Weglaufen]";
        List<String> options = OptionsParser.parse(text);

        assertEquals(List.of("Leise gehen", "Zauber wirken", "Weglaufen"), options);
    }

    @Test
    void ignoresMissingOptions() {
        List<String> options = OptionsParser.parse("Keine Optionen hier.");

        assertEquals(List.of(), options);
    }
}
