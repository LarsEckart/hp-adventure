package com.example.hpadventure.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("smoke")
final class OpenRouterTextProviderSmokeTest {
    private static final String PRODUCTION_BASE_URL = "https://openrouter.ai/api";
    private static final String DEFAULT_MODEL = "xiaomi/mimo-v2-flash:free";

    @Test
    void createMessage_hitsProduction() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENROUTER_API_KEY must be set for smoke test");

        OpenRouterTextProvider provider = buildProvider(apiKey);
        String response = provider.createMessage(
            "You are a test harness. Reply with the word OK.",
            List.of(new TextProvider.Message("user", "Please respond with OK.")),
            32
        );

        assertFalse(response.isBlank());
    }

    @Test
    void streamMessage_hitsProduction() {
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENROUTER_API_KEY must be set for smoke test");

        OpenRouterTextProvider provider = buildProvider(apiKey);
        StringBuilder builder = new StringBuilder();
        AtomicInteger chunks = new AtomicInteger();

        provider.streamMessage(
            "You are a test harness. Reply with the word OK.",
            List.of(new TextProvider.Message("user", "Stream back OK.")),
            32,
            delta -> {
                chunks.incrementAndGet();
                builder.append(delta);
            }
        );

        assertTrue(chunks.get() > 0, "Expected streaming chunks");
        assertFalse(builder.toString().isBlank());
    }

    private static OpenRouterTextProvider buildProvider(String apiKey) {
        String model = System.getenv().getOrDefault("OPENROUTER_TEXT_MODEL", DEFAULT_MODEL);
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(60))
            .build();
        ObjectMapper mapper = new ObjectMapper();

        return new OpenRouterTextProvider(httpClient, mapper, apiKey, model, PRODUCTION_BASE_URL);
    }
}
