package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;
import com.example.hpadventure.clients.AnthropicClient;
import com.example.hpadventure.clients.OpenAiImageClient;
import com.example.hpadventure.parsing.CompletionParser;
import com.example.hpadventure.parsing.ItemParser;
import com.example.hpadventure.parsing.MarkerCleaner;
import com.example.hpadventure.parsing.OptionsParser;
import com.example.hpadventure.parsing.SceneParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StoryServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void nextTurn_parsesMarkers_and_generatesTitle() throws Exception {
        try (MockWebServer anthropic = new MockWebServer(); MockWebServer openAi = new MockWebServer()) {
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

            anthropic.enqueue(new MockResponse().setResponseCode(200).setBody(anthropicResponse(rawStory)));
            anthropic.enqueue(new MockResponse().setResponseCode(200).setBody(anthropicResponse("Der Nordturm")));
            openAi.enqueue(new MockResponse().setResponseCode(200).setBody(openAiResponse("base64data")));

            anthropic.start();
            openAi.start();

            Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
            StoryService service = buildService(anthropic, openAi, clock);

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
            assertEquals("Der Nordturm", assistant.adventure().title());
            assertNull(assistant.adventure().summary());
            assertNull(assistant.adventure().completedAt());

            assertEquals("image/webp", assistant.image().mimeType());
            assertEquals("base64data", assistant.image().base64());
            assertTrue(assistant.image().prompt().contains("Dunkler Korridor mit Kerzenlicht"));

            assertEquals(2, anthropic.getRequestCount());
            RecordedRequest storyRequest = anthropic.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(storyRequest);
            assertEquals("/v1/messages", storyRequest.getPath());
            RecordedRequest titleRequest = anthropic.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(titleRequest);
            assertEquals("/v1/messages", titleRequest.getPath());

            assertEquals(1, openAi.getRequestCount());
            RecordedRequest imageRequest = openAi.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(imageRequest);
            assertEquals("/v1/images/generations", imageRequest.getPath());
        }
    }

    @Test
    void nextTurn_generatesSummary_onCompletion() throws Exception {
        try (MockWebServer anthropic = new MockWebServer(); MockWebServer openAi = new MockWebServer()) {
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

            anthropic.enqueue(new MockResponse().setResponseCode(200).setBody(anthropicResponse(rawStory)));
            anthropic.enqueue(new MockResponse().setResponseCode(200).setBody(anthropicResponse("Kurze Zusammenfassung.")));
            openAi.enqueue(new MockResponse().setResponseCode(200).setBody(openAiResponse("finalimage")));

            anthropic.start();
            openAi.start();

            Clock clock = Clock.fixed(Instant.parse("2026-01-01T11:00:00Z"), ZoneOffset.UTC);
            StoryService service = buildService(anthropic, openAi, clock);

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

            assertEquals(2, anthropic.getRequestCount());
            RecordedRequest storyRequest = anthropic.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(storyRequest);
            assertEquals("/v1/messages", storyRequest.getPath());
            RecordedRequest summaryRequest = anthropic.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(summaryRequest);
            assertEquals("/v1/messages", summaryRequest.getPath());
        }
    }

    private StoryService buildService(MockWebServer anthropic, MockWebServer openAi, Clock clock) {
        OkHttpClient httpClient = new OkHttpClient.Builder().build();
        AnthropicClient anthropicClient = new AnthropicClient(
            httpClient,
            MAPPER,
            "test-key",
            "test-model",
            baseUrl(anthropic)
        );
        OpenAiImageClient openAiClient = new OpenAiImageClient(
            httpClient,
            MAPPER,
            "test-key",
            "gpt-image-1",
            baseUrl(openAi),
            "webp",
            70,
            "low",
            "1024x1024"
        );

        return new StoryService(
            anthropicClient,
            new PromptBuilder(),
            new ItemParser(clock),
            new CompletionParser(),
            new OptionsParser(),
            new SceneParser(),
            new MarkerCleaner(),
            new TitleService(anthropicClient),
            new SummaryService(anthropicClient),
            new ImagePromptService(),
            openAiClient,
            clock
        );
    }

    private static String baseUrl(MockWebServer server) {
        String url = server.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String anthropicResponse(String text) throws Exception {
        Map<String, Object> payload = Map.of(
            "content", List.of(Map.of("type", "text", "text", text))
        );
        return MAPPER.writeValueAsString(payload);
    }

    private static String openAiResponse(String base64) throws Exception {
        Map<String, Object> payload = Map.of(
            "data", List.of(Map.of("b64_json", base64))
        );
        return MAPPER.writeValueAsString(payload);
    }
}
