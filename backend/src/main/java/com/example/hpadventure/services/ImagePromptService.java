package com.example.hpadventure.services;

public final class ImagePromptService {
    private static final String STYLE_PREFIX =
        "Stimmungsvolle, detailreiche Fantasy-Illustration im Stil klassischer Buchkunst. "
            + "Weiches Licht, klare Komposition, keine Texte oder Logos. "
            + "Keine Personen oder Charaktere zeigen; keine Porträts. "
            + "Zeige nur Landschaften, Orte, Gegenstände oder Gegner/Kreaturen/Tiere. Szene: ";

    private static final int FALLBACK_LIMIT = 220;

    public String buildPrompt(String scene, String storyText) {
        String cleanedScene = safeTrim(scene);
        if (cleanedScene == null) {
            cleanedScene = fallbackScene(storyText);
        }
        if (cleanedScene == null || cleanedScene.isBlank()) {
            cleanedScene = "Hogwarts bei Nacht, magische Atmosphäre";
        }
        return STYLE_PREFIX + cleanedScene.trim();
    }

    private String fallbackScene(String storyText) {
        if (storyText == null || storyText.isBlank()) {
            return null;
        }

        String[] lines = storyText.trim().split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return shorten(trimmed);
            }
        }

        return null;
    }

    private String shorten(String text) {
        if (text == null || text.length() <= FALLBACK_LIMIT) {
            return text;
        }
        return text.substring(0, FALLBACK_LIMIT).trim();
    }

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
