package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;

import com.example.hpadventure.parsing.ItemParser;
import com.example.hpadventure.parsing.MarkerCleaner;
import com.example.hpadventure.parsing.OptionsParser;
import com.example.hpadventure.parsing.SceneParser;
import com.example.hpadventure.providers.ImageProvider;
import com.example.hpadventure.providers.TextProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class StoryStreamServiceTest {
    @Test
    void streamTurn_emitsDeltas_and_returnsAssistant() throws Exception {
        String partOne = String.join("\n",
            "Du trittst in den Gang und hoerst das Knistern der Fackeln.",
            "",
            ""
        );
        String partTwo = String.join("\n",
            "Was tust du?",
            "",
            "[OPTION: Leise umsehen]",
            "[OPTION: Weitergehen]",
            "[SZENE: Dunkler Korridor mit Kerzenlicht]",
            "[NEUER GEGENSTAND: Schluessel | Ein alter, schwerer Schluessel]"
        );
        String filteredPartTwo = "Was tust du?\n\n\n\n\n";

        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        StoryService service = buildService(partOne, partTwo, clock);

        Dtos.StoryRequest request = new Dtos.StoryRequest(
            new Dtos.Player("Hermine", "Gryffindor", List.of(), List.of(), new Dtos.Stats(0, 0)),
            new Dtos.CurrentAdventure(null, "2026-01-01T09:00:00Z"),
            List.of(new Dtos.ChatMessage("assistant", "Vorherige Szene")),
            "Ich oeffne die Tuer."
        );

        List<String> deltas = new ArrayList<>();
        StoryStreamHandler.StreamResult result = service.streamTurn(request, deltas::add);
        Dtos.Assistant assistant = result.assistant();

        assertEquals(2, deltas.size());
        assertEquals(partOne, deltas.get(0));
        assertEquals(filteredPartTwo, deltas.get(1));

        assertEquals(
            "Du trittst in den Gang und hoerst das Knistern der Fackeln.\n\nWas tust du?",
            assistant.storyText()
        );
        assertEquals(List.of("Leise umsehen", "Weitergehen"), assistant.suggestedActions());
        assertEquals("Schluessel", assistant.newItems().get(0).name());
        assertEquals("2026-01-01T10:00:00Z", assistant.newItems().get(0).foundAt());
        assertFalse(assistant.adventure().completed());
        assertEquals("Ravenclaws Verborgenes Geheimnis", assistant.adventure().title());
        assertNull(assistant.image());

        Dtos.Image image = service.generateImage(result.imagePrompt());
        assertNotNull(image);
    }

    private StoryService buildService(String partOne, String partTwo, Clock clock) {
        TextProvider textProvider = new FakeTextProvider(
            List.of(List.of(partOne, partTwo)),
            List.of("# Ravenclaws Verborgenes Geheimnis Das ist ein spannendes Abenteuer! Der Titel fasst die zentrale Mystery zusammen.")
        );
        ImageProvider imageProvider = new FakeImageProvider(true, "image/webp", "base64data");

        return new StoryService(
            textProvider,
            new PromptBuilder(),
            new ItemParser(clock),
            new OptionsParser(),
            new SceneParser(),
            new MarkerCleaner(),
            new TitleService(textProvider),
            new SummaryService(textProvider),
            new ImagePromptService(),
            imageProvider,
            clock
        );
    }
}
