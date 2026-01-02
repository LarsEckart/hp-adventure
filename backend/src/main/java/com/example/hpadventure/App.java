package com.example.hpadventure;

import com.example.hpadventure.api.AuthRoutes;
import com.example.hpadventure.api.HealthRoutes;
import com.example.hpadventure.api.StoryRoutes;
import com.example.hpadventure.api.TtsRoutes;
import com.example.hpadventure.config.RateLimiter;
import com.example.hpadventure.providers.AnthropicTextProvider;
import com.example.hpadventure.providers.ElevenLabsSpeechProvider;
import com.example.hpadventure.providers.ImageProvider;
import com.example.hpadventure.providers.OpenAiImageProvider;
import com.example.hpadventure.providers.OpenRouterImageProvider;
import com.example.hpadventure.providers.OpenRouterTextProvider;
import com.example.hpadventure.providers.SpeechProvider;
import com.example.hpadventure.providers.TextProvider;
import com.example.hpadventure.parsing.CompletionParser;
import com.example.hpadventure.parsing.ItemParser;
import com.example.hpadventure.parsing.MarkerCleaner;
import com.example.hpadventure.parsing.OptionsParser;
import com.example.hpadventure.parsing.SceneParser;
import com.example.hpadventure.services.ImagePromptService;
import com.example.hpadventure.services.PromptBuilder;
import com.example.hpadventure.services.StoryService;
import com.example.hpadventure.services.SummaryService;
import com.example.hpadventure.services.TtsService;
import com.example.hpadventure.services.TitleService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;

public final class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("=".repeat(60));
        logger.info("HP Adventure Server starting...");
        logger.info("=".repeat(60));
        
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(60))
            .build();

        String anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");
        String anthropicModel = System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-sonnet-4-5");
        String anthropicBaseUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://api.anthropic.com");

        // OpenAI image config
        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        String openAiBaseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com");
        String openAiModel = System.getenv().getOrDefault("OPENAI_IMAGE_MODEL", "gpt-image-1");
        String openAiFormat = System.getenv().getOrDefault("OPENAI_IMAGE_FORMAT", "webp");
        String openAiQuality = System.getenv().getOrDefault("OPENAI_IMAGE_QUALITY", "low");
        String openAiSize = System.getenv().getOrDefault("OPENAI_IMAGE_SIZE", "1024x1024");
        Integer openAiCompression = parseIntOrNull(System.getenv().getOrDefault("OPENAI_IMAGE_COMPRESSION", "70"));

        // OpenRouter config (text + image)
        String openRouterApiKey = System.getenv("OPENROUTER_API_KEY");
        String openRouterBaseUrl = System.getenv().getOrDefault("OPENROUTER_BASE_URL", "https://openrouter.ai/api");
        String openRouterTextModel = System.getenv().getOrDefault("OPENROUTER_TEXT_MODEL", "xiaomi/mimo-v2-flash:free");
        String openRouterImageModel = System.getenv().getOrDefault("OPENROUTER_IMAGE_MODEL", "google/gemini-2.5-flash-image");

        String elevenLabsApiKey = System.getenv("ELEVENLABS_API_KEY");
        String elevenLabsVoiceId = System.getenv().getOrDefault("ELEVENLABS_VOICE_ID", "g1jpii0iyvtRs8fqXsd1");
        String elevenLabsModel = System.getenv().getOrDefault("ELEVENLABS_MODEL", "eleven_multilingual_v2");
        String elevenLabsBaseUrl = System.getenv().getOrDefault("ELEVENLABS_BASE_URL", "https://api.elevenlabs.io");
        String elevenLabsOutputFormat = System.getenv("ELEVENLABS_OUTPUT_FORMAT");
        Integer elevenLabsOptimizeLatency = parseIntOrNull(System.getenv("ELEVENLABS_OPTIMIZE_STREAMING_LATENCY"));

        // Select image provider: prefer OpenRouter, fall back to OpenAI
        ImageProvider imageProvider = createImageProvider(
            httpClient, mapper,
            openRouterApiKey, openRouterImageModel, openRouterBaseUrl,
            openAiApiKey, openAiModel, openAiBaseUrl, openAiFormat, openAiCompression, openAiQuality, openAiSize
        );

        // Select text provider: prefer OpenRouter, fall back to Anthropic
        TextProvider textProvider = createTextProvider(
            httpClient, mapper,
            openRouterApiKey, openRouterTextModel, openRouterBaseUrl,
            anthropicApiKey, anthropicModel, anthropicBaseUrl
        );
        SpeechProvider speechProvider = new ElevenLabsSpeechProvider(
            httpClient,
            mapper,
            elevenLabsApiKey,
            elevenLabsVoiceId,
            elevenLabsModel,
            elevenLabsBaseUrl,
            elevenLabsOutputFormat,
            elevenLabsOptimizeLatency
        );
        Integer rateLimitPerMinute = parseIntOrNull(System.getenv("RATE_LIMIT_PER_MINUTE"));
        if (rateLimitPerMinute == null) {
            rateLimitPerMinute = 2;
        }
        RateLimiter rateLimiter = rateLimitPerMinute > 0
            ? new RateLimiter(Clock.systemUTC(), rateLimitPerMinute, Duration.ofMinutes(1))
            : null;
        PromptBuilder promptBuilder = new PromptBuilder();
        ItemParser itemParser = new ItemParser(Clock.systemUTC());
        CompletionParser completionParser = new CompletionParser();
        OptionsParser optionsParser = new OptionsParser();
        SceneParser sceneParser = new SceneParser();
        MarkerCleaner markerCleaner = new MarkerCleaner();
        TitleService titleService = new TitleService(textProvider);
        SummaryService summaryService = new SummaryService(textProvider);
        ImagePromptService imagePromptService = new ImagePromptService();
        StoryService storyService = new StoryService(
            textProvider,
            promptBuilder,
            itemParser,
            completionParser,
            optionsParser,
            sceneParser,
            markerCleaner,
            titleService,
            summaryService,
            imagePromptService,
            imageProvider,
            Clock.systemUTC()
        );
        TtsService ttsService = new TtsService(speechProvider);

        // Authentication
        String appPasswords = System.getenv("APP_PASSWORDS");
        AuthRoutes authRoutes = new AuthRoutes(appPasswords);

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, false));
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
            config.spaRoot.addFile("/", "/public/index.html");
        });

        // Global exception handler for any uncaught exceptions
        app.exception(Exception.class, (e, ctx) -> {
            logger.error("Unhandled exception on {} {}: {}", ctx.method(), ctx.path(), e.getMessage(), e);
            if (!ctx.res().isCommitted()) {
                ctx.status(500).json(new com.example.hpadventure.api.Dtos.ErrorResponse(
                    new com.example.hpadventure.api.Dtos.ErrorResponse.Error(
                        "INTERNAL_ERROR", 
                        "Unexpected server error", 
                        null
                    )
                ));
            }
        });

        HealthRoutes.register(app);
        authRoutes.register(app);
        
        // Apply auth middleware to protected routes
        if (authRoutes.isEnabled()) {
            app.before("/api/story", authRoutes.authMiddleware());
            app.before("/api/story/*", authRoutes.authMiddleware());
            app.before("/api/tts", authRoutes.authMiddleware());
        }
        
        StoryRoutes.register(app, storyService, rateLimiter);
        TtsRoutes.register(app, ttsService);

        app.start(port);
        
        logger.info("=".repeat(60));
        logger.info("HP Adventure Server started successfully");
        logger.info("Listening on port {}", port);
        logger.info("Rate limit: {} requests/minute {}", rateLimitPerMinute, rateLimitPerMinute > 0 ? "(enabled)" : "(disabled)");
        logger.info("Authentication: {}", authRoutes.isEnabled() ? "enabled" : "disabled");
        logger.info("Text provider: {}", textProvider.getClass().getSimpleName());
        logger.info("Image provider: {}", imageProvider.isEnabled() ? imageProvider.getClass().getSimpleName() : "disabled");
        logger.info("Speech provider: {}", (elevenLabsApiKey != null && !elevenLabsApiKey.isBlank()) ? speechProvider.getClass().getSimpleName() : "disabled");
        logger.info("=".repeat(60));
    }

    /**
     * Create image provider based on available API keys.
     * Priority: OPENROUTER_API_KEY > OPENAI_API_KEY
     */
    private static ImageProvider createImageProvider(
        OkHttpClient httpClient,
        ObjectMapper mapper,
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
        // Prefer OpenRouter if configured
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
        
        // Fall back to OpenAI
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
        
        // Return disabled OpenRouter provider as placeholder
        logger.warn("No image API key configured (OPENROUTER_API_KEY or OPENAI_API_KEY)");
        return new OpenRouterImageProvider(
            httpClient,
            mapper,
            null,
            openRouterModel,
            openRouterBaseUrl
        );
    }

    /**
     * Create text provider based on available API keys.
     * Priority: OPENROUTER_API_KEY > ANTHROPIC_API_KEY
     */
    private static TextProvider createTextProvider(
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
        
        // Return disabled Anthropic provider as placeholder (will fail on use)
        logger.warn("No text API key configured (OPENROUTER_API_KEY or ANTHROPIC_API_KEY)");
        return new AnthropicTextProvider(
            httpClient,
            mapper,
            null,
            anthropicModel,
            anthropicBaseUrl
        );
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
