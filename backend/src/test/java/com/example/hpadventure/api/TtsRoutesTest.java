package com.example.hpadventure.api;

import com.example.hpadventure.services.TtsHandler;
import com.example.hpadventure.services.UpstreamException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TtsRoutesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void postTts_streamsAudio() {
        TtsHandler handler = (text, outputStream) -> {
            try {
                outputStream.write("audio".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        Javalin app = buildApp(handler);

        JavalinTest.test(app, (server, client) -> {
            try (Response response = client.post("/api/tts", new Dtos.TtsRequest("Hallo")) ) {
                assertEquals(200, response.code());
                assertNotNull(response.body());
                String contentType = response.header("Content-Type");
                assertNotNull(contentType);
                assertTrue(contentType.startsWith("audio/mpeg"));
                byte[] body = response.body().bytes();
                assertEquals("audio", new String(body, StandardCharsets.UTF_8));
            }
        });
    }

    @Test
    void postTts_requiresText() {
        TtsHandler handler = (text, outputStream) -> {};

        Javalin app = buildApp(handler);

        JavalinTest.test(app, (server, client) -> {
            try (Response response = client.post("/api/tts", new Dtos.TtsRequest(" ")) ) {
                assertEquals(400, response.code());
                Dtos.ErrorResponse error = readResponse(response, Dtos.ErrorResponse.class);
                assertEquals("INVALID_REQUEST", error.error().code());
            }
        });
    }

    @Test
    void postTts_mapsUpstreamErrors() {
        TtsHandler handler = (text, outputStream) -> {
            throw new UpstreamException("ELEVENLABS_ERROR", 503, "unavailable");
        };

        Javalin app = buildApp(handler);

        JavalinTest.test(app, (server, client) -> {
            try (Response response = client.post("/api/tts", new Dtos.TtsRequest("Hallo")) ) {
                assertEquals(503, response.code());
                Dtos.ErrorResponse error = readResponse(response, Dtos.ErrorResponse.class);
                assertEquals("ELEVENLABS_ERROR", error.error().code());
            }
        });
    }

    private static Javalin buildApp(TtsHandler handler) {
        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson(MAPPER, false)));
        TtsRoutes.register(app, handler);
        return app;
    }

    private static <T> T readResponse(Response response, Class<T> type) throws Exception {
        assertNotNull(response.body());
        return MAPPER.readValue(response.body().string(), type);
    }
}
