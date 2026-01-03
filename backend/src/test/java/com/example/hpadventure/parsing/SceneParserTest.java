package com.example.hpadventure.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SceneParserTest {
    @Test
    void returnsSceneWhenPresent() {
        String text = "Story.\n[SZENE: Hogwarts bei Nacht, Kerzenlicht]";
        assertEquals("Hogwarts bei Nacht, Kerzenlicht", SceneParser.parse(text));
    }

    @Test
    void returnsNullWhenMissing() {
        assertNull(SceneParser.parse("Story without marker."));
    }
}
