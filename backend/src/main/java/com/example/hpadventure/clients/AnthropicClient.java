package com.example.hpadventure.clients;

import com.example.hpadventure.services.UpstreamException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class AnthropicClient {
    private static final Logger logger = LoggerFactory.getLogger(AnthropicClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String VERSION_HEADER = "2023-06-01";
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public AnthropicClient(OkHttpClient httpClient, ObjectMapper mapper, String apiKey, String model, String baseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.apiKey = apiKey;
        this.model = Objects.requireNonNull(model, "model");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
    }

    public String createMessage(String systemPrompt, List<Message> messages, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UpstreamException("MISSING_ANTHROPIC_API_KEY", 500, "ANTHROPIC_API_KEY is not set");
        }

        CreateMessageRequest requestBody = new CreateMessageRequest(model, maxTokens, systemFrom(systemPrompt), messages);

        String url = baseUrl + "/v1/messages";
        logger.info("Anthropic request: POST {} model={} maxTokens={} messagesCount={}", 
            url, model, maxTokens, messages.size());
        long startTime = System.nanoTime();

        try {
            byte[] payload = mapper.writeValueAsBytes(requestBody);
            Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", VERSION_HEADER)
                .post(RequestBody.create(payload, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("Anthropic response: status={} durationMs={}", response.code(), durationMs);
                
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    logger.warn("Anthropic error: status={} body={}", response.code(), errorBody);
                    throw new UpstreamException("ANTHROPIC_ERROR", response.code(), errorBody);
                }

                if (response.body() == null) {
                    throw new UpstreamException("ANTHROPIC_ERROR", response.code(), "Empty response body");
                }

                CreateMessageResponse responseBody = mapper.readValue(response.body().bytes(), CreateMessageResponse.class);
                return responseBody.text();
            }
        } catch (IOException e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.error("Anthropic request failed: durationMs={} error={}", durationMs, e.getMessage());
            throw new UpstreamException("ANTHROPIC_ERROR", 502, e.getMessage(), e);
        }
    }

    public void streamMessage(String systemPrompt, List<Message> messages, int maxTokens, Consumer<String> onDelta) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UpstreamException("MISSING_ANTHROPIC_API_KEY", 500, "ANTHROPIC_API_KEY is not set");
        }
        Objects.requireNonNull(onDelta, "onDelta");

        CreateMessageStreamRequest requestBody = new CreateMessageStreamRequest(
            model,
            maxTokens,
            systemFrom(systemPrompt),
            messages,
            true
        );

        String url = baseUrl + "/v1/messages";
        logger.info("Anthropic stream request: POST {} model={} maxTokens={} messagesCount={}",
            url, model, maxTokens, messages.size());
        long startTime = System.nanoTime();

        try {
            byte[] payload = mapper.writeValueAsBytes(requestBody);
            Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", VERSION_HEADER)
                .addHeader("accept", "text/event-stream")
                .post(RequestBody.create(payload, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long firstByteMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("Anthropic stream response: status={} timeToFirstByteMs={}", response.code(), firstByteMs);
                
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    logger.warn("Anthropic stream error: status={} body={}", response.code(), errorBody);
                    throw new UpstreamException("ANTHROPIC_ERROR", response.code(), errorBody);
                }

                if (response.body() == null) {
                    throw new UpstreamException("ANTHROPIC_ERROR", response.code(), "Empty response body");
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

                    StreamEvent event = mapper.readValue(data, StreamEvent.class);
                    if (event == null || !"content_block_delta".equals(event.type()) || event.delta() == null) {
                        continue;
                    }
                    if (!"text_delta".equals(event.delta().type())) {
                        continue;
                    }
                    String text = event.delta().text();
                    if (text != null && !text.isEmpty()) {
                        onDelta.accept(text);
                    }
                }
                
                long totalMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("Anthropic stream completed: totalDurationMs={}", totalMs);
            }
        } catch (IOException e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.error("Anthropic stream request failed: durationMs={} error={}", durationMs, e.getMessage());
            throw new UpstreamException("ANTHROPIC_ERROR", 502, e.getMessage(), e);
        }
    }

    public record Message(String role, String content) {
    }

    public record CreateMessageRequest(String model, int max_tokens, List<SystemContent> system, List<Message> messages) {
    }

    public record CreateMessageStreamRequest(
        String model,
        int max_tokens,
        List<SystemContent> system,
        List<Message> messages,
        boolean stream
    ) {
    }

    public record SystemContent(String type, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateMessageResponse(List<ContentBlock> content) {
        public String text() {
            if (content == null || content.isEmpty()) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            for (ContentBlock block : content) {
                if (block != null && "text".equals(block.type()) && block.text() != null) {
                    builder.append(block.text());
                }
            }
            return builder.toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamEvent(String type, StreamDelta delta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamDelta(String type, String text) {
    }

    private static List<SystemContent> systemFrom(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return List.of();
        }
        return List.of(new SystemContent("text", systemPrompt));
    }

}
