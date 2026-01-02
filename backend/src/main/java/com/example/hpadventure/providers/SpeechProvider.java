package com.example.hpadventure.providers;

import java.io.OutputStream;

/**
 * Interface for text-to-speech providers.
 * Implementations can use different backends (ElevenLabs, etc.)
 */
public interface SpeechProvider {

    /**
     * Stream synthesized speech audio to the output stream.
     *
     * @param text         The text to convert to speech
     * @param outputStream The stream to write audio data to
     * @throws com.example.hpadventure.services.UpstreamException if synthesis fails
     */
    void streamSpeech(String text, OutputStream outputStream);
}
