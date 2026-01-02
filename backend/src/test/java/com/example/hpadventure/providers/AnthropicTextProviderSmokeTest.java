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
final class AnthropicTextProviderSmokeTest {
    private static final String PRODUCTION_BASE_URL = "https://api.anthropic.com";

    @Test
    void createMessage_hitsProduction() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "ANTHROPIC_API_KEY must be set for smoke test");

        AnthropicTextProvider provider = buildProvider(apiKey);
        String response = provider.createMessage(
            "You are a test harness. Reply with the word OK.",
            List.of(new TextProvider.Message("user", "Please respond with OK.")),
            32
        );

        assertFalse(response.isBlank());
    }

    @Test
    void streamMessage_hitsProduction() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "ANTHROPIC_API_KEY must be set for smoke test");

        AnthropicTextProvider provider = buildProvider(apiKey);
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

    private static AnthropicTextProvider buildProvider(String apiKey) {
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-sonnet-4-5");
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(60))
            .build();
        ObjectMapper mapper = new ObjectMapper();

        return new AnthropicTextProvider(httpClient, mapper, apiKey, model, PRODUCTION_BASE_URL);
    }
}
