package com.example.hpadventure.providers;

import com.example.hpadventure.config.EnvUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating SpeechProvider instances based on environment configuration.
 */
public final class SpeechProviderFactory {
    private static final Logger logger = LoggerFactory.getLogger(SpeechProviderFactory.class);

    private static final String DEFAULT_ELEVENLABS_BASE_URL = "https://api.elevenlabs.io";
    private static final String DEFAULT_ELEVENLABS_VOICE_ID = "g1jpii0iyvtRs8fqXsd1";
    private static final String DEFAULT_ELEVENLABS_MODEL = "eleven_multilingual_v2";

    private SpeechProviderFactory() {
    }

    /**
     * Create a SpeechProvider from environment variables.
     */
    public static SpeechProvider fromEnv(OkHttpClient httpClient, ObjectMapper mapper) {
        String apiKey = System.getenv("ELEVENLABS_API_KEY");
        String voiceId = System.getenv().getOrDefault("ELEVENLABS_VOICE_ID", DEFAULT_ELEVENLABS_VOICE_ID);
        String model = System.getenv().getOrDefault("ELEVENLABS_MODEL", DEFAULT_ELEVENLABS_MODEL);
        String baseUrl = System.getenv().getOrDefault("ELEVENLABS_BASE_URL", DEFAULT_ELEVENLABS_BASE_URL);
        String outputFormat = System.getenv("ELEVENLABS_OUTPUT_FORMAT");
        Integer optimizeLatency = EnvUtils.parseIntOrNull(System.getenv("ELEVENLABS_OPTIMIZE_STREAMING_LATENCY"));

        return create(httpClient, mapper, apiKey, voiceId, model, baseUrl, outputFormat, optimizeLatency);
    }

    /**
     * Create a SpeechProvider with explicit configuration.
     */
    public static SpeechProvider create(
        OkHttpClient httpClient,
        ObjectMapper mapper,
        String apiKey,
        String voiceId,
        String model,
        String baseUrl,
        String outputFormat,
        Integer optimizeLatency
    ) {
        if (apiKey != null && !apiKey.isBlank()) {
            logger.info("Using ElevenLabs for speech (voiceId={})", voiceId);
        } else {
            logger.warn("No speech API key configured (ELEVENLABS_API_KEY)");
        }

        return new ElevenLabsSpeechProvider(
            httpClient,
            mapper,
            apiKey,
            voiceId != null ? voiceId : DEFAULT_ELEVENLABS_VOICE_ID,
            model != null ? model : DEFAULT_ELEVENLABS_MODEL,
            baseUrl != null ? baseUrl : DEFAULT_ELEVENLABS_BASE_URL,
            outputFormat,
            optimizeLatency
        );
    }

}
