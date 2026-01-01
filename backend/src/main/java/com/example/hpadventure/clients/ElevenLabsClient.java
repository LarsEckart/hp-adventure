package com.example.hpadventure.clients;

import com.example.hpadventure.services.UpstreamException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public final class ElevenLabsClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String voiceId;
    private final String modelId;
    private final String baseUrl;
    private final String outputFormat;
    private final Integer optimizeStreamingLatency;

    public ElevenLabsClient(
        OkHttpClient httpClient,
        ObjectMapper mapper,
        String apiKey,
        String voiceId,
        String modelId,
        String baseUrl,
        String outputFormat,
        Integer optimizeStreamingLatency
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.apiKey = apiKey;
        this.voiceId = voiceId;
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.outputFormat = outputFormat;
        this.optimizeStreamingLatency = optimizeStreamingLatency;
    }

    public void streamSpeech(String text, OutputStream outputStream) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UpstreamException("MISSING_ELEVENLABS_API_KEY", 500, "ELEVENLABS_API_KEY is not set");
        }
        if (voiceId == null || voiceId.isBlank()) {
            throw new UpstreamException("MISSING_ELEVENLABS_VOICE_ID", 500, "ELEVENLABS_VOICE_ID is not set");
        }
        if (text == null || text.isBlank()) {
            throw new UpstreamException("INVALID_TTS_TEXT", 400, "text is required");
        }
        Objects.requireNonNull(outputStream, "outputStream");

        TextToSpeechRequest requestBody = new TextToSpeechRequest(text, modelId);

        try {
            byte[] payload = mapper.writeValueAsBytes(requestBody);
            Request request = new Request.Builder()
                .url(buildUrl())
                .addHeader("xi-api-key", apiKey)
                .addHeader("Accept", "audio/mpeg")
                .post(RequestBody.create(payload, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new UpstreamException("ELEVENLABS_ERROR", response.code(), errorBody);
                }

                if (response.body() == null) {
                    throw new UpstreamException("ELEVENLABS_ERROR", response.code(), "Empty response body");
                }

                try (InputStream input = response.body().byteStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                        outputStream.flush();
                    }
                }
            }
        } catch (IOException e) {
            throw new UpstreamException("ELEVENLABS_ERROR", 502, e.getMessage(), e);
        }
    }

    private HttpUrl buildUrl() {
        HttpUrl base = HttpUrl.parse(baseUrl + "/v1/text-to-speech/" + voiceId + "/stream");
        if (base == null) {
            throw new UpstreamException("ELEVENLABS_ERROR", 500, "Invalid ELEVENLABS_BASE_URL");
        }
        HttpUrl.Builder builder = base.newBuilder();
        if (outputFormat != null && !outputFormat.isBlank()) {
            builder.addQueryParameter("output_format", outputFormat);
        }
        if (optimizeStreamingLatency != null && optimizeStreamingLatency >= 0) {
            builder.addQueryParameter("optimize_streaming_latency", String.valueOf(optimizeStreamingLatency));
        }
        return builder.build();
    }

    public record TextToSpeechRequest(String text, String model_id) {
    }
}
