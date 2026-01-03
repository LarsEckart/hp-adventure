package com.example.hpadventure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemicolonSeparatedPairsTest {

    @Test
    void validConfig() {
        var pairs = SemicolonSeparatedPairs.from("anna:secret1,tom:secret2,lisa:magic3");
        var map = pairs.toMap();

        assertEquals(3, map.size());
        assertEquals("secret1", map.get("anna"));
        assertEquals("secret2", map.get("tom"));
        assertEquals("magic3", map.get("lisa"));
    }

    @Test
    void nullConfig() {
        var pairs = SemicolonSeparatedPairs.from(null);
        assertTrue(pairs.toMap().isEmpty());
    }

    @Test
    void emptyConfig() {
        var pairs = SemicolonSeparatedPairs.from("");
        assertTrue(pairs.toMap().isEmpty());
    }

    @Test
    void blankConfig() {
        var pairs = SemicolonSeparatedPairs.from("   ");
        assertTrue(pairs.toMap().isEmpty());
    }

    @Test
    void withWhitespace() {
        var pairs = SemicolonSeparatedPairs.from("  anna : secret1 , tom:secret2  ");
        var map = pairs.toMap();

        assertEquals(2, map.size());
        assertEquals("secret1", map.get("anna"));
        assertEquals("secret2", map.get("tom"));
    }

    @Test
    void invalidEntriesSkipped() {
        // Entries without colon or empty parts should be skipped
        var pairs = SemicolonSeparatedPairs.from("invalid,anna:secret1,:novalue,nokey:,valid:pwd");
        var map = pairs.toMap();

        assertEquals(2, map.size());
        assertEquals("secret1", map.get("anna"));
        assertEquals("pwd", map.get("valid"));
        assertNull(map.get("invalid"));
    }

    @Test
    void singleEntry() {
        var pairs = SemicolonSeparatedPairs.from("user:pass");
        var map = pairs.toMap();

        assertEquals(1, map.size());
        assertEquals("pass", map.get("user"));
    }

    @Test
    void colonInValue() {
        // Colon in value should be preserved (only split on first colon)
        var pairs = SemicolonSeparatedPairs.from("user:pass:with:colons");
        var map = pairs.toMap();

        assertEquals(1, map.size());
        assertEquals("pass:with:colons", map.get("user"));
    }
}
