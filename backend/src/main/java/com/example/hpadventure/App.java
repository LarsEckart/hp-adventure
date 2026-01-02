package com.example.hpadventure;

import com.example.hpadventure.api.HealthRoutes;
import com.example.hpadventure.api.StoryRoutes;
import com.example.hpadventure.api.TtsRoutes;
import com.example.hpadventure.config.RateLimiter;
import com.example.hpadventure.clients.AnthropicClient;
import com.example.hpadventure.clients.ElevenLabsClient;
import com.example.hpadventure.clients.OpenAiImageClient;
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

import java.time.Clock;
import java.time.Duration;

public final class App {
    public static void main(String[] args) {
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

        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        String openAiBaseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com");
        String openAiModel = System.getenv().getOrDefault("OPENAI_IMAGE_MODEL", "gpt-image-1");
        String openAiFormat = System.getenv().getOrDefault("OPENAI_IMAGE_FORMAT", "webp");
        String openAiQuality = System.getenv().getOrDefault("OPENAI_IMAGE_QUALITY", "low");
        String openAiSize = System.getenv().getOrDefault("OPENAI_IMAGE_SIZE", "1024x1024");
        Integer openAiCompression = parseIntOrNull(System.getenv().getOrDefault("OPENAI_IMAGE_COMPRESSION", "70"));

        String elevenLabsApiKey = System.getenv("ELEVENLABS_API_KEY");
        String elevenLabsVoiceId = System.getenv().getOrDefault("ELEVENLABS_VOICE_ID", "g1jpii0iyvtRs8fqXsd1");
        String elevenLabsModel = System.getenv().getOrDefault("ELEVENLABS_MODEL", "eleven_multilingual_v2");
        String elevenLabsBaseUrl = System.getenv().getOrDefault("ELEVENLABS_BASE_URL", "https://api.elevenlabs.io");
        String elevenLabsOutputFormat = System.getenv("ELEVENLABS_OUTPUT_FORMAT");
        Integer elevenLabsOptimizeLatency = parseIntOrNull(System.getenv("ELEVENLABS_OPTIMIZE_STREAMING_LATENCY"));

        AnthropicClient anthropicClient = new AnthropicClient(httpClient, mapper, anthropicApiKey, anthropicModel, anthropicBaseUrl);
        OpenAiImageClient openAiImageClient = new OpenAiImageClient(
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
        ElevenLabsClient elevenLabsClient = new ElevenLabsClient(
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
        TitleService titleService = new TitleService(anthropicClient);
        SummaryService summaryService = new SummaryService(anthropicClient);
        ImagePromptService imagePromptService = new ImagePromptService();
        StoryService storyService = new StoryService(
            anthropicClient,
            promptBuilder,
            itemParser,
            completionParser,
            optionsParser,
            sceneParser,
            markerCleaner,
            titleService,
            summaryService,
            imagePromptService,
            openAiImageClient,
            Clock.systemUTC()
        );
        TtsService ttsService = new TtsService(elevenLabsClient);

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, false));
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
            config.spaRoot.addFile("/", "/public/index.html");
        });

        HealthRoutes.register(app);
        StoryRoutes.register(app, storyService, rateLimiter);
        TtsRoutes.register(app, ttsService);

        app.start(port);
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
