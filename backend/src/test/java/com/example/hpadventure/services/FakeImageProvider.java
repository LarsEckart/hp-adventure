package com.example.hpadventure.services;

import com.example.hpadventure.providers.ImageProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class FakeImageProvider implements ImageProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String outputFormat;
    private final Integer outputCompression;
    private final String quality;
    private final String size;

    FakeImageProvider(
        OkHttpClient httpClient,
        ObjectMapper mapper,
        String apiKey,
        String model,
        String baseUrl,
        String outputFormat,
        Integer outputCompression,
        String quality,
        String size
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.apiKey = apiKey;
        this.model = Objects.requireNonNull(model, "model");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.outputFormat = outputFormat;
        this.outputCompression = outputCompression;
        this.quality = quality;
        this.size = size;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ImageResult generateImage(String prompt) {
        if (!isEnabled()) {
            throw new UpstreamException("MISSING_OPENAI_API_KEY", 500, "OPENAI_API_KEY is not set");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new UpstreamException("INVALID_IMAGE_PROMPT", 400, "Image prompt is required");
        }

        CreateImageRequest requestBody = new CreateImageRequest(
            model,
            prompt,
            size,
            quality,
            outputFormat,
            outputCompression,
            1
        );

        String url = baseUrl + "/v1/images/generations";
        try {
            byte[] payload = mapper.writeValueAsBytes(requestBody);
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(payload, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new UpstreamException("OPENAI_IMAGE_ERROR", response.code(), errorBody);
                }

                if (response.body() == null) {
                    throw new UpstreamException("OPENAI_IMAGE_ERROR", response.code(), "Empty response body");
                }

                ImageResponse responseBody = mapper.readValue(response.body().bytes(), ImageResponse.class);
                String base64 = responseBody.firstImage();
                if (base64 == null || base64.isBlank()) {
                    throw new UpstreamException("OPENAI_IMAGE_ERROR", response.code(), "No image data returned");
                }

                String mimeType = formatToMimeType(outputFormat);
                return new ImageResult(mimeType, base64);
            }
        } catch (IOException e) {
            throw new UpstreamException("OPENAI_IMAGE_ERROR", 502, e.getMessage(), e);
        }
    }

    private String formatToMimeType(String format) {
        if (format == null || format.isBlank()) {
            return "image/png";
        }
        String normalized = format.toLowerCase(Locale.ROOT).trim();
        if ("jpg".equals(normalized) || "jpeg".equals(normalized)) {
            return "image/jpeg";
        }
        if ("webp".equals(normalized)) {
            return "image/webp";
        }
        if ("png".equals(normalized)) {
            return "image/png";
        }
        return "image/" + normalized;
    }

    private record CreateImageRequest(
        String model,
        String prompt,
        String size,
        String quality,
        String output_format,
        Integer output_compression,
        Integer n
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ImageResponse(List<ImageData> data) {
        public String firstImage() {
            if (data == null || data.isEmpty()) {
                return null;
            }
            ImageData first = data.get(0);
            return first == null ? null : first.b64_json();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ImageData(String b64_json) {
    }
}
