package com.example.hpadventure;

import com.example.hpadventure.api.AuthRoutes;
import com.example.hpadventure.api.HealthRoutes;
import com.example.hpadventure.api.StoryRoutes;
import com.example.hpadventure.api.TtsRoutes;
import com.example.hpadventure.config.EnvUtils;
import com.example.hpadventure.config.RateLimiter;
import com.example.hpadventure.providers.ImageProvider;
import com.example.hpadventure.providers.ImageProviderFactory;
import com.example.hpadventure.providers.SpeechProvider;
import com.example.hpadventure.providers.SpeechProviderFactory;
import com.example.hpadventure.providers.TextProvider;
import com.example.hpadventure.providers.TextProviderFactory;

import com.example.hpadventure.parsing.ItemParser;



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

        // Create providers via factories
        TextProvider textProvider = TextProviderFactory.fromEnv(httpClient, mapper);
        ImageProvider imageProvider = ImageProviderFactory.fromEnv(httpClient, mapper);
        SpeechProvider speechProvider = SpeechProviderFactory.fromEnv(httpClient, mapper);

        // Rate limiter
        Integer rateLimitPerMinute = EnvUtils.parseIntOrNull(System.getenv("RATE_LIMIT_PER_MINUTE"));
        if (rateLimitPerMinute == null) {
            rateLimitPerMinute = 2;
        }
        RateLimiter rateLimiter = rateLimitPerMinute > 0
            ? new RateLimiter(Clock.systemUTC(), rateLimitPerMinute, Duration.ofMinutes(1))
            : null;

        // Parsers and services
        PromptBuilder promptBuilder = new PromptBuilder();
        ItemParser itemParser = new ItemParser(Clock.systemUTC());
        TitleService titleService = new TitleService(textProvider);
        SummaryService summaryService = new SummaryService(textProvider);
        ImagePromptService imagePromptService = new ImagePromptService();
        StoryService storyService = new StoryService(
            textProvider,
            promptBuilder,
            itemParser,
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
        logger.info("Speech provider: {}", speechProvider.getClass().getSimpleName());
        logger.info("=".repeat(60));
    }

}
