package com.example.hpadventure.clients;

import com.example.hpadventure.services.UpstreamException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class AnthropicClient {
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

        CreateMessageRequest requestBody = new CreateMessageRequest(model, maxTokens, systemPrompt, messages);

        try {
            byte[] payload = mapper.writeValueAsBytes(requestBody);
            Request request = new Request.Builder()
                .url(baseUrl + "/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", VERSION_HEADER)
                .post(RequestBody.create(payload, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new UpstreamException("ANTHROPIC_ERROR", response.code(), errorBody);
                }

                if (response.body() == null) {
                    throw new UpstreamException("ANTHROPIC_ERROR", response.code(), "Empty response body");
                }

                CreateMessageResponse responseBody = mapper.readValue(response.body().bytes(), CreateMessageResponse.class);
                return responseBody.text();
            }
        } catch (IOException e) {
            throw new UpstreamException("ANTHROPIC_ERROR", 502, e.getMessage(), e);
        }
    }

    public record Message(String role, String content) {
    }

    public record CreateMessageRequest(String model, int max_tokens, String system, List<Message> messages) {
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
}
