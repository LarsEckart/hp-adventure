package com.example.hpadventure.parsing;

import java.util.List;

public final class StreamMarkerFilter {
    private static final List<String> MARKER_PREFIXES = List.of(
        "NEUER GEGENSTAND:",
        "ABENTEUER ABGESCHLOSSEN",
        "OPTION:",
        "SZENE:"
    );

    private final StringBuilder buffer = new StringBuilder();
    private boolean inCandidate = false;

    public String apply(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < delta.length(); i++) {
            char current = delta.charAt(i);
            if (!inCandidate) {
                if (current == '[') {
                    inCandidate = true;
                    buffer.setLength(0);
                } else {
                    output.append(current);
                }
            } else if (current == ']') {
                if (!isMarkerContent(buffer)) {
                    output.append('[').append(buffer).append(']');
                }
                buffer.setLength(0);
                inCandidate = false;
            } else {
                buffer.append(current);
                if (!isPossibleMarker(buffer)) {
                    output.append('[').append(buffer);
                    buffer.setLength(0);
                    inCandidate = false;
                }
            }
        }

        return output.toString();
    }

    private static boolean isPossibleMarker(CharSequence content) {
        String trimmed = trimLeading(content);
        if (trimmed.isEmpty()) {
            return true;
        }

        for (String prefix : MARKER_PREFIXES) {
            if (prefix.startsWith(trimmed) || trimmed.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMarkerContent(CharSequence content) {
        String trimmed = trimLeading(content);
        if (trimmed.isEmpty()) {
            return false;
        }
        for (String prefix : MARKER_PREFIXES) {
            if (trimmed.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String trimLeading(CharSequence content) {
        return content.toString().stripLeading();
    }
}
