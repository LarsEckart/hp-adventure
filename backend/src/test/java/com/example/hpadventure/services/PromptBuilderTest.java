package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {
    @Test
    void includesPlayerInventoryAndHistory() {
        PromptBuilder builder = new PromptBuilder();

        Dtos.Item item = new Dtos.Item("Zauberstab", "Ein zuverlässiger Begleiter", "2026-01-01T10:00:00Z");
        Dtos.CompletedAdventure completed = new Dtos.CompletedAdventure("Die Flüsternde Rüstung", "Ein Geheimfach entdeckt.", "2026-01-01T11:00:00Z");
        Dtos.Player player = new Dtos.Player("Hermine", "Gryffindor", List.of(item), List.of(completed), new Dtos.Stats(1, 3));

        String prompt = builder.build(player, 3);

        assertTrue(prompt.contains("Name: Hermine"));
        assertTrue(prompt.contains("Haus: Gryffindor"));
        assertTrue(prompt.contains("INVENTAR DES SPIELERS"));
        assertTrue(prompt.contains("Zauberstab"));
        assertTrue(prompt.contains("VERGANGENE ABENTEUER"));
        assertTrue(prompt.contains("Die Flüsternde Rüstung"));
        assertTrue(prompt.contains("GESCHICHTENBOGEN"));
        assertTrue(prompt.contains("Schritt: 3 von 15"));
        assertTrue(prompt.contains("[OPTION:"));
        assertTrue(prompt.contains("[SZENE:"));
    }

    @Test
    void includesInventorySectionWhenEmpty() {
        PromptBuilder builder = new PromptBuilder();

        Dtos.Player player = new Dtos.Player("Harry", "Gryffindor", List.of(), List.of(), new Dtos.Stats(0, 0));

        String prompt = builder.build(player, 1);

        assertTrue(prompt.contains("INVENTAR DES SPIELERS"));
        assertTrue(prompt.contains("- (keine)"));
    }
}
