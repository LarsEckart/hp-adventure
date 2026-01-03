package com.example.hpadventure.parsing;

public final class MarkdownSanitizer {
    private MarkdownSanitizer() {
    }

    public static String strip(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
            .replace("*", "")
            .replace("_", "")
            .replace("`", "");
    }
}
