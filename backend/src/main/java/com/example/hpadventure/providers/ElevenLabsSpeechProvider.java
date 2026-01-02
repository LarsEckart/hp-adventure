package com.example.hpadventure.providers;

import com.example.hpadventure.services.UpstreamException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public final class ElevenLabsSpeechProvider implements SpeechProvider {
    private static final Logger logger = LoggerFactory.getLogger(ElevenLabsSpeechProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String voiceId;
    private final String modelId;
    private final String baseUrl;
    private final String outputFormat;
    private final Integer optimizeStreamingLatency;

    public ElevenLabsSpeechProvider(
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

    @Override
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
        HttpUrl url = buildUrl();
        logger.info("ElevenLabs TTS request: POST {} model={} textLength={}", url, modelId, text.length());
        long startTime = System.nanoTime();

        try {
            byte[] payload = mapper.writeValueAsBytes(requestBody);
            Request request = new Request.Builder()
                .url(url)
                .addHeader("xi-api-key", apiKey)
                .addHeader("Accept", "audio/mpeg")
                .post(RequestBody.create(payload, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long firstByteMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("ElevenLabs TTS response: status={} timeToFirstByteMs={}", response.code(), firstByteMs);
                
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    logger.warn("ElevenLabs TTS error: status={} body={}", response.code(), errorBody);
                    throw new UpstreamException("ELEVENLABS_ERROR", response.code(), errorBody);
                }

                if (response.body() == null) {
                    throw new UpstreamException("ELEVENLABS_ERROR", response.code(), "Empty response body");
                }

                long bytesWritten = 0;
                try (InputStream input = response.body().byteStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                        outputStream.flush();
                        bytesWritten += read;
                    }
                }
                
                long totalMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.info("ElevenLabs TTS completed: bytesStreamed={} totalDurationMs={}", bytesWritten, totalMs);
            }
        } catch (IOException e) {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            logger.error("ElevenLabs TTS request failed: durationMs={} error={}", durationMs, e.getMessage());
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

    private record TextToSpeechRequest(String text, String model_id) {
    }
}
