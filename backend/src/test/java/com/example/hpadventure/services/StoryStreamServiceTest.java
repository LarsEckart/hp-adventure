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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class StoryStreamServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void streamTurn_emitsDeltas_and_returnsAssistant() throws Exception {
        try (MockWebServer anthropic = new MockWebServer(); MockWebServer openAi = new MockWebServer()) {
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

            String sseBody = buildSseBody(partOne, partTwo);
            anthropic.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(sseBody));
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

            List<String> deltas = new ArrayList<>();
            Dtos.Assistant assistant = service.streamTurn(request, deltas::add);

            assertEquals(2, deltas.size());
            assertEquals(partOne, deltas.get(0));
            assertEquals(partTwo, deltas.get(1));

            assertEquals(
                "Du trittst in den Gang und hoerst das Knistern der Fackeln.\n\nWas tust du?",
                assistant.storyText()
            );
            assertEquals(List.of("Leise umsehen", "Weitergehen"), assistant.suggestedActions());
            assertEquals("Schluessel", assistant.newItems().get(0).name());
            assertEquals("2026-01-01T10:00:00Z", assistant.newItems().get(0).foundAt());
            assertFalse(assistant.adventure().completed());
            assertEquals("Der Nordturm", assistant.adventure().title());
            assertNotNull(assistant.image());
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

    private static String buildSseBody(String... parts) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("event: message_start\n");
        builder.append("data: ").append(MAPPER.writeValueAsString(Map.of("type", "message_start"))).append("\n\n");
        for (String part : parts) {
            builder.append("event: content_block_delta\n");
            builder.append("data: ").append(deltaPayload(part)).append("\n\n");
        }
        builder.append("event: message_stop\n");
        builder.append("data: ").append(MAPPER.writeValueAsString(Map.of("type", "message_stop"))).append("\n\n");
        return builder.toString();
    }

    private static String deltaPayload(String text) throws Exception {
        Map<String, Object> payload = Map.of(
            "type", "content_block_delta",
            "delta", Map.of("type", "text_delta", "text", text)
        );
        return MAPPER.writeValueAsString(payload);
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
