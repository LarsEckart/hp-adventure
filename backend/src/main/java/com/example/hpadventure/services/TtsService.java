package com.example.hpadventure.services;

import com.example.hpadventure.providers.SpeechProvider;

import java.io.OutputStream;
import java.util.Objects;

public final class TtsService implements TtsHandler {
    private final SpeechProvider speechProvider;

    public TtsService(SpeechProvider speechProvider) {
        this.speechProvider = Objects.requireNonNull(speechProvider, "speechProvider");
    }

    @Override
    public void stream(String text, OutputStream outputStream) {
        speechProvider.streamSpeech(text, outputStream);
    }
}
