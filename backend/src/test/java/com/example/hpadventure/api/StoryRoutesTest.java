package com.example.hpadventure.api;

import com.example.hpadventure.config.RateLimiter;
import com.example.hpadventure.services.StoryHandler;
import com.example.hpadventure.services.UpstreamException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class StoryRoutesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void postStory_returnsAssistant() {
        StoryHandler handler = request -> new Dtos.Assistant(
            "Der Korridor ist still. Was tust du?",
            List.of("Weitergehen", "Umkehren"),
            new Dtos.Adventure("Der Nordturm", false, null, null),
            new Dtos.Image("image/webp", "base64", "Dunkler Korridor")
        );

        Javalin app = buildApp(handler, null);

        JavalinTest.test(app, (server, client) -> {
            try (Response response = client.post("/api/story", sampleRequest("start"))) {
                assertEquals(200, response.code());
                Dtos.StoryResponse body = readResponse(response, Dtos.StoryResponse.class);
                assertEquals("Der Korridor ist still. Was tust du?", body.assistant().storyText());
                assertEquals("Der Nordturm", body.assistant().adventure().title());
                assertEquals("base64", body.assistant().image().base64());
            }
        });
    }

    @Test
    void postStory_requiresAction() {
        AtomicInteger calls = new AtomicInteger();
        StoryHandler handler = request -> {
            calls.incrementAndGet();
            return minimalAssistant();
        };

        Javalin app = buildApp(handler, null);

        JavalinTest.test(app, (server, client) -> {
            try (Response response = client.post("/api/story", sampleRequest(" "))) {
                assertEquals(400, response.code());
                Dtos.ErrorResponse error = readResponse(response, Dtos.ErrorResponse.class);
                assertEquals("INVALID_REQUEST", error.error().code());
            }
        });

        assertEquals(0, calls.get());
    }

    @Test
    void postStory_rateLimitsRequests() {
        AtomicInteger calls = new AtomicInteger();
        StoryHandler handler = request -> {
            calls.incrementAndGet();
            return minimalAssistant();
        };

        RateLimiter limiter = new RateLimiter(
            Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC),
            1,
            Duration.ofMinutes(1)
        );

        Javalin app = buildApp(handler, limiter);

        JavalinTest.test(app, (server, client) -> {
            try (Response response = client.post("/api/story", sampleRequest("start"))) {
                assertEquals(200, response.code());
            }

            try (Response response = client.post("/api/story", sampleRequest("start"))) {
                assertEquals(429, response.code());
                Dtos.ErrorResponse error = readResponse(response, Dtos.ErrorResponse.class);
                assertEquals("RATE_LIMITED", error.error().code());
            }
        });

        assertEquals(1, calls.get());
    }

    @Test
    void postStory_mapsUpstreamErrors() {
        StoryHandler handler = request -> {
            throw new UpstreamException("UPSTREAM_TIMEOUT", 504, "timeout");
        };

        Javalin app = buildApp(handler, null);

        JavalinTest.test(app, (server, client) -> {
            try (Response response = client.post("/api/story", sampleRequest("start"))) {
                assertEquals(504, response.code());
                Dtos.ErrorResponse error = readResponse(response, Dtos.ErrorResponse.class);
                assertEquals("UPSTREAM_TIMEOUT", error.error().code());
            }
        });
    }

    private static Dtos.Assistant minimalAssistant() {
        return new Dtos.Assistant(
            "Test",
            List.of(),
            new Dtos.Adventure(null, false, null, null),
            new Dtos.Image("image/webp", "base64", null)
        );
    }

    private static Javalin buildApp(StoryHandler handler, RateLimiter limiter) {
        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson(MAPPER, false)));
        StoryRoutes.register(app, handler, limiter);
        return app;
    }

    private static Dtos.StoryRequest sampleRequest(String action) {
        return new Dtos.StoryRequest(
            new Dtos.Player("Hermine", "Gryffindor", List.of(), new Dtos.Stats(0, 0)),
            new Dtos.CurrentAdventure("Test", "2026-01-01T10:00:00Z"),
            List.of(),
            action
        );
    }

    private static <T> T readResponse(Response response, Class<T> type) throws Exception {
        assertNotNull(response.body());
        return MAPPER.readValue(response.body().string(), type);
    }
}
