package com.example.hpadventure.api;

import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    public record StoryRequest(
        Player player,
        CurrentAdventure currentAdventure,
        List<ChatMessage> conversationHistory,
        String action
    ) {
    }

    public record TtsRequest(String text) {
    }

    public record Player(
        String name,
        String houseName,
        List<Item> inventory,
        List<CompletedAdventure> completedAdventures,
        Stats stats
    ) {
    }

    public record Item(String name, String description, String foundAt) {
    }

    public record CompletedAdventure(String title, String summary, String completedAt) {
    }

    public record Stats(Integer adventuresCompleted, Integer totalTurns) {
    }

    public record CurrentAdventure(String title, String startedAt) {
    }

    public record ChatMessage(String role, String content) {
    }

    public record StoryResponse(Assistant assistant) {
    }

    public record StreamDelta(String text) {
    }

    public record Assistant(
        String storyText,
        List<String> suggestedActions,
        List<Item> newItems,
        Adventure adventure,
        Image image
    ) {
    }

    public record Adventure(String title, boolean completed, String summary, String completedAt) {
    }

    public record Image(String mimeType, String base64, String prompt) {
    }

    public record ErrorResponse(Error error) {
        public record Error(String code, String message, String requestId) {
        }
    }
}
