package com.example.hpadventure.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating ImageProvider instances based on environment configuration.
 * 
 * If IMAGE_PROVIDER is set to "openai" or "openrouter", that provider is used explicitly.
 * Otherwise, priority is: OPENROUTER_API_KEY > OPENAI_API_KEY
 */
public final class ImageProviderFactory {
    private static final Logger logger = LoggerFactory.getLogger(ImageProviderFactory.class);

    private static final String DEFAULT_OPENROUTER_BASE_URL = "https://openrouter.ai/api";
    private static final String DEFAULT_OPENROUTER_MODEL = "google/gemini-2.5-flash-image";
    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-image-1";
    private static final String DEFAULT_OPENAI_FORMAT = "webp";
    private static final String DEFAULT_OPENAI_QUALITY = "low";
    private static final String DEFAULT_OPENAI_SIZE = "1024x1024";
    private static final int DEFAULT_OPENAI_COMPRESSION = 70;

    private ImageProviderFactory() {
    }

    /**
     * Create an ImageProvider from environment variables.
     */
    public static ImageProvider fromEnv(OkHttpClient httpClient, ObjectMapper mapper) {
        String imageProvider = System.getenv("IMAGE_PROVIDER");

        String openRouterApiKey = System.getenv("OPENROUTER_API_KEY");
        String openRouterBaseUrl = System.getenv().getOrDefault("OPENROUTER_BASE_URL", DEFAULT_OPENROUTER_BASE_URL);
        String openRouterModel = System.getenv().getOrDefault("OPENROUTER_IMAGE_MODEL", DEFAULT_OPENROUTER_MODEL);

        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        String openAiBaseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", DEFAULT_OPENAI_BASE_URL);
        String openAiModel = System.getenv().getOrDefault("OPENAI_IMAGE_MODEL", DEFAULT_OPENAI_MODEL);
        String openAiFormat = System.getenv().getOrDefault("OPENAI_IMAGE_FORMAT", DEFAULT_OPENAI_FORMAT);
        String openAiQuality = System.getenv().getOrDefault("OPENAI_IMAGE_QUALITY", DEFAULT_OPENAI_QUALITY);
        String openAiSize = System.getenv().getOrDefault("OPENAI_IMAGE_SIZE", DEFAULT_OPENAI_SIZE);
        Integer openAiCompression = parseIntOrDefault(System.getenv("OPENAI_IMAGE_COMPRESSION"), DEFAULT_OPENAI_COMPRESSION);

        return create(
            httpClient, mapper,
            imageProvider,
            openRouterApiKey, openRouterModel, openRouterBaseUrl,
            openAiApiKey, openAiModel, openAiBaseUrl, openAiFormat, openAiCompression, openAiQuality, openAiSize
        );
    }

    /**
     * Create an ImageProvider with explicit configuration.
     * 
     * If imageProvider is "openai" or "openrouter", that provider is used explicitly.
     * Otherwise, priority is: OpenRouter > OpenAI
     */
    public static ImageProvider create(
        OkHttpClient httpClient,
        ObjectMapper mapper,
        String imageProvider,
        String openRouterApiKey,
        String openRouterModel,
        String openRouterBaseUrl,
        String openAiApiKey,
        String openAiModel,
        String openAiBaseUrl,
        String openAiFormat,
        Integer openAiCompression,
        String openAiQuality,
        String openAiSize
    ) {
        // Explicit provider override
        if ("openai".equalsIgnoreCase(imageProvider)) {
            if (openAiApiKey == null || openAiApiKey.isBlank()) {
                throw new IllegalStateException("IMAGE_PROVIDER=openai but OPENAI_API_KEY is not set");
            }
            logger.info("Using OpenAI for image generation (explicit, model={})", openAiModel);
            return new OpenAiImageProvider(
                httpClient,
                mapper,
                openAiApiKey,
                openAiModel,
                openAiBaseUrl,
                openAiFormat,
                openAiCompression,
                openAiQuality,
                openAiSize
            );
        }

        if ("openrouter".equalsIgnoreCase(imageProvider)) {
            if (openRouterApiKey == null || openRouterApiKey.isBlank()) {
                throw new IllegalStateException("IMAGE_PROVIDER=openrouter but OPENROUTER_API_KEY is not set");
            }
            logger.info("Using OpenRouter for image generation (explicit, model={})", openRouterModel);
            return new OpenRouterImageProvider(
                httpClient,
                mapper,
                openRouterApiKey,
                openRouterModel,
                openRouterBaseUrl
            );
        }

        // Default priority: OpenRouter > OpenAI
        if (openRouterApiKey != null && !openRouterApiKey.isBlank()) {
            logger.info("Using OpenRouter for image generation (model={})", openRouterModel);
            return new OpenRouterImageProvider(
                httpClient,
                mapper,
                openRouterApiKey,
                openRouterModel,
                openRouterBaseUrl
            );
        }

        if (openAiApiKey != null && !openAiApiKey.isBlank()) {
            logger.info("Using OpenAI for image generation (model={})", openAiModel);
            return new OpenAiImageProvider(
                httpClient,
                mapper,
                openAiApiKey,
                openAiModel,
                openAiBaseUrl,
                openAiFormat,
                openAiCompression,
                openAiQuality,
                openAiSize
            );
        }

        // Return placeholder provider that generates a static "no provider configured" image
        logger.warn("No image API key configured (OPENROUTER_API_KEY or OPENAI_API_KEY), using placeholder");
        return new PlaceholderImageProvider();
    }

    private static Integer parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
