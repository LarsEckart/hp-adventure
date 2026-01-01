package com.example.hpadventure.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamMarkerFilterTest {
    @Test
    void filtersMarkersAcrossChunks() {
        StreamMarkerFilter filter = new StreamMarkerFilter();

        String first = filter.apply("Text [OPTION: Ers");
        String second = filter.apply("te]\nWeiter.");

        assertEquals("Text ", first);
        assertEquals("\nWeiter.", second);
    }

    @Test
    void keepsNonMarkerBrackets() {
        StreamMarkerFilter filter = new StreamMarkerFilter();

        String result = filter.apply("Das ist [kein Marker] ok.");

        assertEquals("Das ist [kein Marker] ok.", result);
    }

    @Test
    void filtersSceneMarker() {
        StreamMarkerFilter filter = new StreamMarkerFilter();

        String result = filter.apply("Text [SZENE: Hogwarts]\n");

        assertEquals("Text \n", result);
    }

    @Test
    void filtersMarkerSplitAtBracket() {
        StreamMarkerFilter filter = new StreamMarkerFilter();

        String first = filter.apply("Text [");
        String second = filter.apply("OPTION: A]");

        assertEquals("Text ", first);
        assertEquals("", second);
    }
}
