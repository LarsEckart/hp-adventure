package com.example.hpadventure.providers;

import com.example.hpadventure.services.UpstreamException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Text generation provider using OpenRouter's chat completions API.
 * Compatible with OpenAI-style /v1/chat/completions endpoint.
 * Default model: xiaomi/mimo-v2-flash:free
 */
final class OpenRouterTextProvider implements TextProvider {
    private static final Logger logger = LoggerFactory.getLogger(OpenRouterTextProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api";
    private static final String DEFAULT_MODEL = "xiaomi/mimo-v2-flash:free";

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public OpenRouterTextProvider(
        OkHttpClient httpClient,
        ObjectMapper mapper,
        String apiKey,
        String model,
        String baseUrl
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.apiKey = apiKey;
        this.model = model != null ? model : DEFAULT_MODEL;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String createMessage(String systemPrompt, List<Message> messages, int maxTokens) {
        if (!isEnabled()) {
            throw new UpstreamException("MISSING_OPENROUTER_API_KEY", 500, "OPENROUTER_API_KEY is not set");
        }

        List<ApiMessage> apiMessages = buildMessages(systemPrompt, messages);
        ChatCompletionRequest requestBody = new ChatCompletionRequest(model, apiMessages, maxTokens, false);

        String url = baseUrl + "/v1/chat/completions";
        logger.info("OpenRouter text request: POST {} model={} maxTokens={} messagesCount={}",
            url, model, maxTokens, messages.size());
        long startTime = System.nanoTime();

        try {
            byte[] payload = mapper.writeValueAsBytes(requestBody);
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("HTTP-Referer", "https://hp-adventure.example.com")
                .addHeader("X-Title", "HP Adventure")
                .post(RequestBody.create(payload, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("OpenRouter text response: status={} durationMs={}", response.code(), durationMs);

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    logger.warn("OpenRouter text error: status={} body={}", response.code(), errorBody);
                    throw new UpstreamException("OPENROUTER_ERROR", response.code(), errorBody);
                }

                if (response.body() == null) {
                    throw new UpstreamException("OPENROUTER_ERROR", response.code(), "Empty response body");
                }

                ChatCompletionResponse responseBody = mapper.readValue(response.body().bytes(), ChatCompletionResponse.class);
                return responseBody.text();
            }
        } catch (IOException e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.error("OpenRouter text request failed: durationMs={} error={}", durationMs, e.getMessage());
            throw new UpstreamException("OPENROUTER_ERROR", 502, e.getMessage(), e);
        }
    }

    @Override
    public void streamMessage(String systemPrompt, List<Message> messages, int maxTokens, Consumer<String> onDelta) {
        if (!isEnabled()) {
            throw new UpstreamException("MISSING_OPENROUTER_API_KEY", 500, "OPENROUTER_API_KEY is not set");
        }
        Objects.requireNonNull(onDelta, "onDelta");

        List<ApiMessage> apiMessages = buildMessages(systemPrompt, messages);
        ChatCompletionRequest requestBody = new ChatCompletionRequest(model, apiMessages, maxTokens, true);

        String url = baseUrl + "/v1/chat/completions";
        logger.info("OpenRouter text stream request: POST {} model={} maxTokens={} messagesCount={}",
            url, model, maxTokens, messages.size());
        long startTime = System.nanoTime();

        try {
            byte[] payload = mapper.writeValueAsBytes(requestBody);
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("HTTP-Referer", "https://hp-adventure.example.com")
                .addHeader("X-Title", "HP Adventure")
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(payload, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long firstByteMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("OpenRouter text stream response: status={} timeToFirstByteMs={}", response.code(), firstByteMs);

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    logger.warn("OpenRouter text stream error: status={} body={}", response.code(), errorBody);
                    throw new UpstreamException("OPENROUTER_ERROR", response.code(), errorBody);
                }

                if (response.body() == null) {
                    throw new UpstreamException("OPENROUTER_ERROR", response.code(), "Empty response body");
                }

                BufferedSource source = response.body().source();
                while (true) {
                    String line = source.readUtf8Line();
                    if (line == null) {
                        break;
                    }
                    if (line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }

                    StreamChunk chunk = mapper.readValue(data, StreamChunk.class);
                    if (chunk == null || chunk.choices() == null || chunk.choices().isEmpty()) {
                        continue;
                    }
                    StreamChoice choice = chunk.choices().get(0);
                    if (choice == null || choice.delta() == null) {
                        continue;
                    }
                    String content = choice.delta().content();
                    if (content != null && !content.isEmpty()) {
                        onDelta.accept(content);
                    }
                }

                long totalMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("OpenRouter text stream completed: totalDurationMs={}", totalMs);
            }
        } catch (IOException e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.error("OpenRouter text stream request failed: durationMs={} error={}", durationMs, e.getMessage());
            throw new UpstreamException("OPENROUTER_ERROR", 502, e.getMessage(), e);
        }
    }

    /**
     * Build API messages list, prepending system prompt as a system message if present.
     */
    private List<ApiMessage> buildMessages(String systemPrompt, List<Message> messages) {
        List<ApiMessage> apiMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            apiMessages.add(new ApiMessage("system", systemPrompt));
        }
        for (Message m : messages) {
            apiMessages.add(new ApiMessage(m.role(), m.content()));
        }
        return apiMessages;
    }

    // Request DTOs
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionRequest(
        String model,
        List<ApiMessage> messages,
        int max_tokens,
        boolean stream
    ) {
    }

    private record ApiMessage(String role, String content) {
    }

    // Response DTOs (non-streaming)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(List<Choice> choices) {
        public String text() {
            if (choices == null || choices.isEmpty()) {
                return "";
            }
            Choice first = choices.get(0);
            if (first == null || first.message() == null) {
                return "";
            }
            return first.message().content() != null ? first.message().content() : "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(ResponseMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseMessage(String role, String content) {
    }

    // Streaming DTOs
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StreamChunk(List<StreamChoice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StreamChoice(StreamDelta delta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StreamDelta(String content) {
    }
}
