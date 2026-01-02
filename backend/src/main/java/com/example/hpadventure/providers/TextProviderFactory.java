package com.example.hpadventure.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating TextProvider instances based on environment configuration.
 * Priority: OPENROUTER_API_KEY > ANTHROPIC_API_KEY
 */
public final class TextProviderFactory {
    private static final Logger logger = LoggerFactory.getLogger(TextProviderFactory.class);

    private static final String DEFAULT_OPENROUTER_BASE_URL = "https://openrouter.ai/api";
    private static final String DEFAULT_OPENROUTER_MODEL = "xiaomi/mimo-v2-flash:free";
    private static final String DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-5";

    private TextProviderFactory() {
    }

    /**
     * Create a TextProvider from environment variables.
     */
    public static TextProvider fromEnv(OkHttpClient httpClient, ObjectMapper mapper) {
        String openRouterApiKey = System.getenv("OPENROUTER_API_KEY");
        String openRouterBaseUrl = System.getenv().getOrDefault("OPENROUTER_BASE_URL", DEFAULT_OPENROUTER_BASE_URL);
        String openRouterModel = System.getenv().getOrDefault("OPENROUTER_TEXT_MODEL", DEFAULT_OPENROUTER_MODEL);

        String anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");
        String anthropicBaseUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", DEFAULT_ANTHROPIC_BASE_URL);
        String anthropicModel = System.getenv().getOrDefault("ANTHROPIC_MODEL", DEFAULT_ANTHROPIC_MODEL);

        return create(
            httpClient, mapper,
            openRouterApiKey, openRouterModel, openRouterBaseUrl,
            anthropicApiKey, anthropicModel, anthropicBaseUrl
        );
    }

    /**
     * Create a TextProvider with explicit configuration.
     * Priority: OpenRouter > Anthropic
     */
    public static TextProvider create(
        OkHttpClient httpClient,
        ObjectMapper mapper,
        String openRouterApiKey,
        String openRouterModel,
        String openRouterBaseUrl,
        String anthropicApiKey,
        String anthropicModel,
        String anthropicBaseUrl
    ) {
        // Prefer OpenRouter if configured
        if (openRouterApiKey != null && !openRouterApiKey.isBlank()) {
            logger.info("Using OpenRouter for text generation (model={})", openRouterModel);
            return new OpenRouterTextProvider(
                httpClient,
                mapper,
                openRouterApiKey,
                openRouterModel,
                openRouterBaseUrl
            );
        }

        // Fall back to Anthropic
        if (anthropicApiKey != null && !anthropicApiKey.isBlank()) {
            logger.info("Using Anthropic for text generation (model={})", anthropicModel);
            return new AnthropicTextProvider(
                httpClient,
                mapper,
                anthropicApiKey,
                anthropicModel,
                anthropicBaseUrl
            );
        }

        // Text generation is required - fail fast
        throw new IllegalStateException(
            "No text API key configured. Set OPENROUTER_API_KEY or ANTHROPIC_API_KEY environment variable."
        );
    }
}
