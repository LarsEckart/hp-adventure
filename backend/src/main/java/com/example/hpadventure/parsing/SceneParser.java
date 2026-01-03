package com.example.hpadventure.parsing;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SceneParser {
    private static final Pattern SCENE_PATTERN = Pattern.compile("\\[SZENE:\\s*([^\\]]+)\\]");

    private SceneParser() {
    }

    public static String parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher matcher = SCENE_PATTERN.matcher(text);
        if (matcher.find()) {
            String scene = matcher.group(1).trim();
            return scene.isEmpty() ? null : scene;
        }

        return null;
    }
}
