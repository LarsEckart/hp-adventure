package com.example.hpadventure.services;

import com.example.hpadventure.clients.ElevenLabsClient;

import java.io.OutputStream;
import java.util.Objects;

public final class TtsService implements TtsHandler {
    private final ElevenLabsClient elevenLabsClient;

    public TtsService(ElevenLabsClient elevenLabsClient) {
        this.elevenLabsClient = Objects.requireNonNull(elevenLabsClient, "elevenLabsClient");
    }

    @Override
    public void stream(String text, OutputStream outputStream) {
        elevenLabsClient.streamSpeech(text, outputStream);
    }
}
