package com.example.hpadventure.services;

import com.example.hpadventure.providers.ImageProvider;

import java.util.Objects;

final class FakeImageProvider implements ImageProvider {
    private final boolean enabled;
    private final String mimeType;
    private final String base64;
    private int generateCalls = 0;
    private String lastPrompt;

    FakeImageProvider(boolean enabled, String mimeType, String base64) {
        this.enabled = enabled;
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        this.base64 = Objects.requireNonNull(base64, "base64");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public ImageResult generateImage(String prompt) {
        generateCalls += 1;
        lastPrompt = prompt;
        if (!enabled) {
            throw new UpstreamException("MISSING_OPENAI_API_KEY", 500, "OPENAI_API_KEY is not set");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new UpstreamException("INVALID_IMAGE_PROMPT", 400, "Image prompt is required");
        }
        return new ImageResult(mimeType, base64);
    }

    int generateCallCount() {
        return generateCalls;
    }

    String lastPrompt() {
        return lastPrompt;
    }
}
