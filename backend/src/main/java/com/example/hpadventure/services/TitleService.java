package com.example.hpadventure.services;

import com.example.hpadventure.clients.AnthropicClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TitleService {
    private static final String TITLE_PROMPT = "Gib diesem Harry Potter Abenteuer einen kurzen, spannenden deutschen Titel (max 5 Wörter, ohne Anführungszeichen):\n\n";
    private static final int MAX_TITLE_WORDS = 5;
    private static final Set<String> TRAILING_STOPWORDS = Set.of(
        "der",
        "die",
        "das",
        "den",
        "dem",
        "des",
        "ein",
        "eine",
        "einer",
        "eines",
        "einem",
        "ist",
        "im",
        "in",
        "am",
        "an",
        "und",
        "oder",
        "zur",
        "zum",
        "von",
        "vom",
        "mit",
        "für",
        "fur",
        "auf",
        "aus",
        "bei",
        "über",
        "uber",
        "unter",
        "ohne"
    );

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
        String response = anthropicClient.createMessage(null, List.of(new AnthropicClient.Message("user", prompt)), 50);
        return sanitizeTitle(response);
    }

    private static String sanitizeTitle(String response) {
        if (response == null) {
            return null;
        }

        String cleaned = response.trim().replace("\"", "").replace("'", "");
        if (cleaned.isEmpty()) {
            return null;
        }

        String firstLine = cleaned.split("\\R", 2)[0].trim();
        if (firstLine.startsWith("#")) {
            firstLine = firstLine.replaceFirst("^#+\\s*", "");
        }
        String lower = firstLine.toLowerCase(Locale.ROOT);
        if (lower.startsWith("titel:")) {
            firstLine = firstLine.substring("titel:".length()).trim();
        } else if (lower.startsWith("title:")) {
            firstLine = firstLine.substring("title:".length()).trim();
        }

        String normalized = firstLine.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }

        String[] words = normalized.split("\\s+");
        if (words.length <= MAX_TITLE_WORDS) {
            return normalized;
        }

        List<String> limited = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_TITLE_WORDS, words.length); i++) {
            limited.add(words[i]);
        }

        while (!limited.isEmpty()) {
            String last = limited.get(limited.size() - 1).toLowerCase(Locale.ROOT);
            if (TRAILING_STOPWORDS.contains(last)) {
                limited.remove(limited.size() - 1);
            } else {
                break;
            }
        }

        if (limited.isEmpty()) {
            return normalized;
        }

        return String.join(" ", limited);
    }
}
