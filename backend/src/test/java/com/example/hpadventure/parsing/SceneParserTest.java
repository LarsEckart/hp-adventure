package com.example.hpadventure.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SceneParserTest {
    @Test
    void returnsSceneWhenPresent() {
        SceneParser parser = new SceneParser();

        String text = "Story.\n[SZENE: Hogwarts bei Nacht, Kerzenlicht]";
        assertEquals("Hogwarts bei Nacht, Kerzenlicht", parser.parse(text));
    }

    @Test
    void returnsNullWhenMissing() {
        SceneParser parser = new SceneParser();

        assertNull(parser.parse("Story without marker."));
    }
}
