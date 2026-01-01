package com.example.hpadventure.parsing;

import com.example.hpadventure.api.Dtos;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemParserTest {
    @Test
    void parsesItemsWithTimestamps() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        ItemParser parser = new ItemParser(clock);

        String text = "Du findest etwas. [NEUER GEGENSTAND: Zauberstab | Ein schimmernder Stab]";
        List<Dtos.Item> items = parser.parse(text);

        assertEquals(1, items.size());
        assertEquals("Zauberstab", items.get(0).name());
        assertEquals("Ein schimmernder Stab", items.get(0).description());
        assertEquals("2026-01-01T10:00:00Z", items.get(0).foundAt());
    }

    @Test
    void returnsEmptyListWhenNoMarkers() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        ItemParser parser = new ItemParser(clock);

        List<Dtos.Item> items = parser.parse("Kein Gegenstand hier.");

        assertTrue(items.isEmpty());
    }
}
