package com.example.hpadventure.parsing;

import java.util.regex.Pattern;

public final class MarkerCleaner {
    private static final Pattern MARKERS = Pattern.compile("\\[(NEUER GEGENSTAND:[^\\]]+|ABENTEUER ABGESCHLOSSEN|OPTION:[^\\]]+|SZENE:[^\\]]+)\\]");
    private static final Pattern EXTRA_BLANKS = Pattern.compile("\\n{3,}");

    private MarkerCleaner() {
    }

    public static String strip(String text) {
        if (text == null) {
            return "";
        }

        String withoutMarkers = MARKERS.matcher(text).replaceAll("");
        String compacted = EXTRA_BLANKS.matcher(withoutMarkers).replaceAll("\n\n");
        return compacted.trim();
    }
}
