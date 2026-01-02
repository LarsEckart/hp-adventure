package com.example.hpadventure.services;

import com.example.hpadventure.providers.TextProvider;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class FakeTextProvider implements TextProvider {
    private final Deque<String> createResponses;
    private final Deque<List<String>> streamResponses;

    FakeTextProvider(List<List<String>> streamResponses, List<String> createResponses) {
        Objects.requireNonNull(streamResponses, "streamResponses");
        Objects.requireNonNull(createResponses, "createResponses");
        this.streamResponses = new ArrayDeque<>(streamResponses);
        this.createResponses = new ArrayDeque<>(createResponses);
    }

    @Override
    public String createMessage(String systemPrompt, List<Message> messages, int maxTokens) {
        if (createResponses.isEmpty()) {
            throw new UpstreamException("FAKE_TEXT_PROVIDER_EMPTY", 500, "No queued text response");
        }
        return createResponses.removeFirst();
    }

    @Override
    public void streamMessage(String systemPrompt, List<Message> messages, int maxTokens, Consumer<String> onDelta) {
        Objects.requireNonNull(onDelta, "onDelta");
        if (streamResponses.isEmpty()) {
            throw new UpstreamException("FAKE_TEXT_PROVIDER_EMPTY", 500, "No queued stream response");
        }
        List<String> deltas = streamResponses.removeFirst();
        for (String delta : deltas) {
            if (delta != null) {
                onDelta.accept(delta);
            }
        }
    }
}
