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

/**
 * Image generation client using OpenRouter's chat completions API.
 * Works with models like google/gemini-2.5-flash-image that output images
 * via the standard chat completions endpoint.
 */
public final class OpenRouterImageClient implements ImageClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public OpenRouterImageClient(
        OkHttpClient httpClient,
        ObjectMapper mapper,
        String apiKey,
        String model,
        String baseUrl
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.apiKey = apiKey;
        this.model = Objects.requireNonNull(model, "model");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ImageResult generateImage(String prompt) {
        if (!isEnabled()) {
            throw new UpstreamException("MISSING_OPENROUTER_API_KEY", 500, "OPENROUTER_API_KEY is not set");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new UpstreamException("INVALID_IMAGE_PROMPT", 400, "Image prompt is required");
        }

        ChatCompletionRequest requestBody = new ChatCompletionRequest(
            model,
            List.of(new Message("user", prompt))
        );

        try {
            byte[] payload = mapper.writeValueAsBytes(requestBody);
            Request request = new Request.Builder()
                .url(baseUrl + "/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("HTTP-Referer", "https://hp-adventure.example.com")
                .addHeader("X-Title", "HP Adventure")
                .post(RequestBody.create(payload, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new UpstreamException("OPENROUTER_IMAGE_ERROR", response.code(), errorBody);
                }

                if (response.body() == null) {
                    throw new UpstreamException("OPENROUTER_IMAGE_ERROR", response.code(), "Empty response body");
                }

                ChatCompletionResponse responseBody = mapper.readValue(response.body().bytes(), ChatCompletionResponse.class);
                ImageData imageData = responseBody.extractImage();
                if (imageData == null) {
                    throw new UpstreamException("OPENROUTER_IMAGE_ERROR", response.code(), "No image data in response");
                }

                return new ImageResult(imageData.mimeType(), imageData.base64());
            }
        } catch (IOException e) {
            throw new UpstreamException("OPENROUTER_IMAGE_ERROR", 502, e.getMessage(), e);
        }
    }

    // Request DTOs
    public record ChatCompletionRequest(
        String model,
        List<Message> messages
    ) {
    }

    public record Message(
        String role,
        String content
    ) {
    }

    // Response DTOs
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionResponse(List<Choice> choices) {
        public ImageData extractImage() {
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Choice first = choices.get(0);
            if (first == null || first.message() == null) {
                return null;
            }
            return first.message().extractImage();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(ResponseMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseMessage(Object content) {
        /**
         * Extract image from content which can be:
         * - A list of content blocks (standard multimodal format)
         * - A string (no image)
         */
        @SuppressWarnings("unchecked")
        public ImageData extractImage() {
            if (content == null) {
                return null;
            }
            
            // Content is typically a list of content blocks for multimodal responses
            if (content instanceof List<?> contentList) {
                for (Object item : contentList) {
                    if (item instanceof java.util.Map<?, ?> block) {
                        String type = (String) block.get("type");
                        if ("image_url".equals(type)) {
                            Object imageUrlObj = block.get("image_url");
                            if (imageUrlObj instanceof java.util.Map<?, ?> imageUrl) {
                                String url = (String) imageUrl.get("url");
                                return parseDataUrl(url);
                            }
                        }
                    }
                }
            }
            
            return null;
        }
        
        private ImageData parseDataUrl(String dataUrl) {
            if (dataUrl == null || !dataUrl.startsWith("data:")) {
                return null;
            }
            // Format: data:image/png;base64,<base64data>
            int commaIndex = dataUrl.indexOf(',');
            if (commaIndex < 0) {
                return null;
            }
            String header = dataUrl.substring(5, commaIndex); // skip "data:"
            String base64 = dataUrl.substring(commaIndex + 1);
            
            // Parse mime type from header like "image/png;base64"
            String mimeType = "image/png"; // default
            int semicolonIndex = header.indexOf(';');
            if (semicolonIndex > 0) {
                mimeType = header.substring(0, semicolonIndex);
            } else if (!header.isEmpty()) {
                mimeType = header;
            }
            
            return new ImageData(mimeType, base64);
        }
    }

    public record ImageData(String mimeType, String base64) {
    }
}
