package com.example.hpadventure;

import com.example.hpadventure.api.Dtos;
import com.example.hpadventure.clients.AnthropicClient;
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
import com.example.hpadventure.services.TitleService;
import com.example.hpadventure.services.UpstreamException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import okhttp3.OkHttpClient;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

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
        String anthropicModel = System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-haiku-4-5-20251001");
        String anthropicBaseUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://api.anthropic.com");

        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        String openAiBaseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com");
        String openAiModel = System.getenv().getOrDefault("OPENAI_IMAGE_MODEL", "gpt-image-1");
        String openAiFormat = System.getenv().getOrDefault("OPENAI_IMAGE_FORMAT", "webp");
        String openAiQuality = System.getenv().getOrDefault("OPENAI_IMAGE_QUALITY", "low");
        String openAiSize = System.getenv().getOrDefault("OPENAI_IMAGE_SIZE", "1024x1024");
        Integer openAiCompression = parseIntOrNull(System.getenv().getOrDefault("OPENAI_IMAGE_COMPRESSION", "70"));

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

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, false));
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });
            config.spaRoot.addFile("/", "/public/index.html");
        });

        app.get("/health", ctx -> ctx.result("ok"));
        app.post("/api/story", ctx -> {
            String requestId = UUID.randomUUID().toString();
            Dtos.StoryRequest request = ctx.bodyAsClass(Dtos.StoryRequest.class);
            String action = request == null ? null : request.action();

            if (action == null || action.isBlank()) {
                ctx.status(400).json(errorResponse("INVALID_REQUEST", "action is required", requestId));
                return;
            }

            try {
                Dtos.Assistant assistant = storyService.nextTurn(request);
                ctx.json(new Dtos.StoryResponse(assistant));
            } catch (UpstreamException e) {
                int status = e.status() >= 400 ? e.status() : 502;
                ctx.status(status).json(errorResponse(e.code(), "Upstream error: " + e.getMessage(), requestId));
            } catch (Exception e) {
                ctx.status(500).json(errorResponse("INTERNAL_ERROR", "Unexpected server error", requestId));
            }
        });

        app.start(port);
    }

    private static Dtos.ErrorResponse errorResponse(String code, String message, String requestId) {
        return new Dtos.ErrorResponse(new Dtos.ErrorResponse.Error(code, message, requestId));
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
