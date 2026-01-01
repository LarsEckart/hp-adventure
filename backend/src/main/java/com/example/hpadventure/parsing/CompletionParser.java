package com.example.hpadventure.parsing;

public final class CompletionParser {
    public boolean isComplete(String text) {
        return text != null && text.contains("[ABENTEUER ABGESCHLOSSEN]");
    }
}
