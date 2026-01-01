package com.example.hpadventure.services;

import com.example.hpadventure.clients.AnthropicClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class TitleService {
    private static final String TITLE_PROMPT = "Gib diesem Harry Potter Abenteuer einen kurzen, spannenden deutschen Titel (max 5 Wörter, ohne Anführungszeichen):\n\n";
    private static final Logger logger = LoggerFactory.getLogger(TitleService.class);

    private final AnthropicClient anthropicClient;

    public TitleService(AnthropicClient anthropicClient) {
        this.anthropicClient = anthropicClient;
    }

    public String generateTitle(List<String> assistantMessages) {
        if (assistantMessages == null || assistantMessages.isEmpty()) {
            return null;
        }

        String joined = String.join("\n", assistantMessages);
        String prompt = TITLE_PROMPT + joined;
        logger.info("Title prompt:\n{}", prompt);
        String response = anthropicClient.createMessage(null, List.of(new AnthropicClient.Message("user", prompt)), 50);
        return response == null ? null : response.trim().replace("\"", "").replace("'", "");
    }
}
