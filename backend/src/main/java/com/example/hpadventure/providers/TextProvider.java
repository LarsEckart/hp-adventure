package com.example.hpadventure.providers;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for text generation providers.
 * Implementations can use different backends (Anthropic, OpenAI, etc.)
 */
public interface TextProvider {

    /**
     * Create a message (non-streaming).
     *
     * @param systemPrompt The system prompt (may be null or empty)
     * @param messages     The conversation history
     * @param maxTokens    Maximum tokens for the response
     * @return The generated text response
     * @throws com.example.hpadventure.services.UpstreamException if generation fails
     */
    String createMessage(String systemPrompt, List<Message> messages, int maxTokens);

    /**
     * Stream a message, calling onDelta for each text chunk.
     *
     * @param systemPrompt The system prompt (may be null or empty)
     * @param messages     The conversation history
     * @param maxTokens    Maximum tokens for the response
     * @param onDelta      Callback for each streamed text delta
     * @throws com.example.hpadventure.services.UpstreamException if generation fails
     */
    void streamMessage(String systemPrompt, List<Message> messages, int maxTokens, Consumer<String> onDelta);

    /**
     * A message in the conversation.
     */
    record Message(String role, String content) {
    }
}
