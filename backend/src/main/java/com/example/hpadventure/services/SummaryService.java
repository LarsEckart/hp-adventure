package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;
import com.example.hpadventure.clients.AnthropicClient;

import java.util.List;

public final class SummaryService {
    private static final String SUMMARY_PROMPT = "Du bist ein Assistent der Text-Adventure Zusammenfassungen erstellt.\n\n"
        + "Fasse das folgende Abenteuer in 2-3 Sätzen zusammen. Erwähne:\n"
        + "- Was passiert ist (Hauptereignisse)\n"
        + "- Welche wichtigen Entscheidungen getroffen wurden\n"
        + "- Wie es endete\n\n"
        + "Schreibe auf Deutsch, in der dritten Person, vergangene Zeit.\n"
        + "Halte es kurz und prägnant (max 50 Wörter).";

    private final AnthropicClient anthropicClient;

    public SummaryService(AnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    public String generateSummary(List<Dtos.ChatMessage> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return null;
        }

        StringBuilder storyContent = new StringBuilder();
        for (Dtos.ChatMessage message : conversationHistory) {
            if (message == null) {
                continue;
            }
            String speaker = "assistant".equals(message.role()) ? "Erzähler" : "Spieler";
            storyContent.append(speaker).append(": ").append(message.content()).append("\n\n");
        }

        String prompt = "Fasse dieses Abenteuer zusammen:\n\n" + storyContent;
        String response = anthropicClient.createMessage(SUMMARY_PROMPT, List.of(new AnthropicClient.Message("user", prompt)), 200);
        return response == null ? null : response.trim();
    }
}
