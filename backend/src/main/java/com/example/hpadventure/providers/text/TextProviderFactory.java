package com.example.hpadventure.providers.text;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating TextProvider instances based on environment configuration.
 * 
 * Explicit override: Set TEXT_PROVIDER=openrouter|anthropic|mistral to force a specific provider.
 * Default priority (when TEXT_PROVIDER not set): OPENROUTER_API_KEY > ANTHROPIC_API_KEY > MISTRAL_API_KEY
 */
public final class TextProviderFactory {
    private static final Logger logger = LoggerFactory.getLogger(TextProviderFactory.class);

    private static final String DEFAULT_OPENROUTER_BASE_URL = "https://openrouter.ai/api";
    public static final String DEFAULT_OPENROUTER_MODEL = "mistralai/mistral-small-3.1-24b-instruct:free";
    private static final String DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-5";
    private static final String DEFAULT_MISTRAL_BASE_URL = "https://api.mistral.ai";
    private static final String DEFAULT_MISTRAL_MODEL = "mistral-small-latest";

    private TextProviderFactory() {
    }

    /**
     * Create a TextProvider from environment variables.
     */
    public static TextProvider fromEnv(OkHttpClient httpClient, ObjectMapper mapper) {
        String textProvider = System.getenv("TEXT_PROVIDER");

        String openRouterApiKey = System.getenv("OPENROUTER_API_KEY");
        String openRouterBaseUrl = System.getenv().getOrDefault("OPENROUTER_BASE_URL", DEFAULT_OPENROUTER_BASE_URL);
        String openRouterModel = System.getenv().getOrDefault("OPENROUTER_TEXT_MODEL", DEFAULT_OPENROUTER_MODEL);

        String anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");
        String anthropicBaseUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", DEFAULT_ANTHROPIC_BASE_URL);
        String anthropicModel = System.getenv().getOrDefault("ANTHROPIC_MODEL", DEFAULT_ANTHROPIC_MODEL);

        String mistralApiKey = System.getenv("MISTRAL_API_KEY");
        String mistralBaseUrl = System.getenv().getOrDefault("MISTRAL_BASE_URL", DEFAULT_MISTRAL_BASE_URL);
        String mistralModel = System.getenv().getOrDefault("MISTRAL_MODEL", DEFAULT_MISTRAL_MODEL);

        return create(
            httpClient, mapper,
            textProvider,
            openRouterApiKey, openRouterModel, openRouterBaseUrl,
            anthropicApiKey, anthropicModel, anthropicBaseUrl,
            mistralApiKey, mistralModel, mistralBaseUrl
        );
    }

    /**
     * Create a TextProvider with explicit configuration.
     * If textProvider is set, use that provider explicitly.
     * Otherwise, priority: OpenRouter > Anthropic > Mistral
     */
    public static TextProvider create(
        OkHttpClient httpClient,
        ObjectMapper mapper,
        String textProvider,
        String openRouterApiKey,
        String openRouterModel,
        String openRouterBaseUrl,
        String anthropicApiKey,
        String anthropicModel,
        String anthropicBaseUrl,
        String mistralApiKey,
        String mistralModel,
        String mistralBaseUrl
    ) {
        // Explicit provider override
        if (textProvider != null && !textProvider.isBlank()) {
            return switch (textProvider.toLowerCase()) {
                case "openrouter" -> {
                    logger.info("Using OpenRouter for text generation (explicit, model={})", openRouterModel);
                    yield new OpenRouterTextProvider(httpClient, mapper, openRouterApiKey, openRouterModel, openRouterBaseUrl);
                }
                case "anthropic" -> {
                    logger.info("Using Anthropic for text generation (explicit, model={})", anthropicModel);
                    yield new AnthropicTextProvider(httpClient, mapper, anthropicApiKey, anthropicModel, anthropicBaseUrl);
                }
                case "mistral" -> {
                    logger.info("Using Mistral for text generation (explicit, model={})", mistralModel);
                    yield new MistralTextProvider(httpClient, mapper, mistralApiKey, mistralModel, mistralBaseUrl);
                }
                default -> throw new IllegalStateException(
                    "Unknown TEXT_PROVIDER: " + textProvider + ". Valid values: openrouter, anthropic, mistral"
                );
            };
        }

        // Default priority: OpenRouter > Anthropic > Mistral
        if (openRouterApiKey != null && !openRouterApiKey.isBlank()) {
            logger.info("Using OpenRouter for text generation (model={})", openRouterModel);
            return new OpenRouterTextProvider(httpClient, mapper, openRouterApiKey, openRouterModel, openRouterBaseUrl);
        }

        if (anthropicApiKey != null && !anthropicApiKey.isBlank()) {
            logger.info("Using Anthropic for text generation (model={})", anthropicModel);
            return new AnthropicTextProvider(httpClient, mapper, anthropicApiKey, anthropicModel, anthropicBaseUrl);
        }

        if (mistralApiKey != null && !mistralApiKey.isBlank()) {
            logger.info("Using Mistral for text generation (model={})", mistralModel);
            return new MistralTextProvider(httpClient, mapper, mistralApiKey, mistralModel, mistralBaseUrl);
        }

        // Text generation is required - fail fast
        throw new IllegalStateException(
            "No text API key configured. Set OPENROUTER_API_KEY, ANTHROPIC_API_KEY, or MISTRAL_API_KEY environment variable."
        );
    }
}
