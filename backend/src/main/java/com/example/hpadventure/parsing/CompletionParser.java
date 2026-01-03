package com.example.hpadventure.parsing;

public final class CompletionParser {
    private CompletionParser() {
    }

    public static boolean isComplete(String text) {
        return text != null && text.contains("[ABENTEUER ABGESCHLOSSEN]");
    }
}
