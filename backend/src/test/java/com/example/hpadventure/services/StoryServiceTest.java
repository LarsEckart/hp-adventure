package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;

import com.example.hpadventure.parsing.ItemParser;
import com.example.hpadventure.parsing.MarkerCleaner;


import com.example.hpadventure.providers.ImageProvider;
import com.example.hpadventure.providers.TextProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StoryServiceTest {
    @Test
    void nextTurn_parsesMarkers_and_generatesTitle() throws Exception {
        String rawStory = String.join("\n",
            "Du trittst in den Gang und hoerst das Knistern der Fackeln.",
            "",
            "Was tust du?",
            "",
            "[OPTION: Leise umsehen]",
            "[OPTION: Weitergehen]",
            "[SZENE: Dunkler Korridor mit Kerzenlicht]",
            "[NEUER GEGENSTAND: Schluessel | Ein alter, schwerer Schluessel]"
        );

        FakeTextProvider textProvider = new FakeTextProvider(
            List.of(),
            List.of(
                rawStory,
                "# Ravenclaws Verborgenes Geheimnis Das ist ein spannendes Abenteuer! Der Titel fasst die zentrale Mystery zusammen."
            )
        );
        FakeImageProvider imageProvider = new FakeImageProvider(true, "image/webp", "base64data");
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        StoryService service = buildService(textProvider, imageProvider, clock);

        Dtos.StoryRequest request = new Dtos.StoryRequest(
            new Dtos.Player("Hermine", "Gryffindor", List.of(), List.of(), new Dtos.Stats(0, 0)),
            new Dtos.CurrentAdventure(null, "2026-01-01T09:00:00Z"),
            List.of(new Dtos.ChatMessage("assistant", "Vorherige Szene")),
            "Ich oeffne die Tuer."
        );

        Dtos.Assistant assistant = service.nextTurn(request);

        assertEquals("Du trittst in den Gang und hoerst das Knistern der Fackeln.\n\nWas tust du?", assistant.storyText());
        assertEquals(List.of("Leise umsehen", "Weitergehen"), assistant.suggestedActions());
        assertEquals(1, assistant.newItems().size());
        assertEquals("Schluessel", assistant.newItems().get(0).name());
        assertEquals("Ein alter, schwerer Schluessel", assistant.newItems().get(0).description());
        assertEquals("2026-01-01T10:00:00Z", assistant.newItems().get(0).foundAt());

        assertFalse(assistant.adventure().completed());
        assertEquals("Ravenclaws Verborgenes Geheimnis", assistant.adventure().title());
        assertNull(assistant.adventure().summary());
        assertNull(assistant.adventure().completedAt());

        assertEquals("image/webp", assistant.image().mimeType());
        assertEquals("base64data", assistant.image().base64());
        assertTrue(assistant.image().prompt().contains("Dunkler Korridor mit Kerzenlicht"));
        assertEquals(2, textProvider.createCallCount());
        assertEquals(0, textProvider.streamCallCount());
        assertEquals(1, imageProvider.generateCallCount());
    }

    @Test
    void nextTurn_generatesSummary_onCompletion() throws Exception {
        String rawStory = String.join("\n",
            "Du hebst den Stein und die Kammer beginnt zu beben.",
            "",
            "Was tust du?",
            "",
            "[OPTION: Herausrennen]",
            "[OPTION: Zauber wirken]",
            "[SZENE: Einstuerzender Gewoelbekeller]",
            "[ABENTEUER ABGESCHLOSSEN]"
        );

        FakeTextProvider textProvider = new FakeTextProvider(
            List.of(),
            List.of(rawStory, "Kurze Zusammenfassung.")
        );
        FakeImageProvider imageProvider = new FakeImageProvider(true, "image/webp", "finalimage");
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T11:00:00Z"), ZoneOffset.UTC);
        StoryService service = buildService(textProvider, imageProvider, clock);

        Dtos.StoryRequest request = new Dtos.StoryRequest(
            new Dtos.Player("Harry", "Gryffindor", List.of(), List.of(), new Dtos.Stats(2, 10)),
            new Dtos.CurrentAdventure("Das Finale", "2026-01-01T10:30:00Z"),
            List.of(new Dtos.ChatMessage("user", "start")),
            "Ich renne los."
        );

        Dtos.Assistant assistant = service.nextTurn(request);

        assertTrue(assistant.adventure().completed());
        assertEquals("Das Finale", assistant.adventure().title());
        assertEquals("Kurze Zusammenfassung.", assistant.adventure().summary());
        assertEquals("2026-01-01T11:00:00Z", assistant.adventure().completedAt());
        assertEquals(2, textProvider.createCallCount());
        assertEquals(0, textProvider.streamCallCount());
        assertEquals(1, imageProvider.generateCallCount());
    }

    private StoryService buildService(TextProvider textProvider, ImageProvider imageProvider, Clock clock) {

        return new StoryService(
            textProvider,
            new PromptBuilder(),
            new ItemParser(clock),
            new MarkerCleaner(),
            new TitleService(textProvider),
            new SummaryService(textProvider),
            new ImagePromptService(),
            imageProvider,
            clock
        );
    }
}
