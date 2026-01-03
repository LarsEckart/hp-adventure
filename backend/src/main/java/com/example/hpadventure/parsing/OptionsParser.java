package com.example.hpadventure.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OptionsParser {
    private static final Pattern OPTION_PATTERN = Pattern.compile("\\[OPTION:\\s*([^\\]]+)\\]");

    private OptionsParser() {
    }

    public static List<String> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
        Matcher matcher = OPTION_PATTERN.matcher(text);
        while (matcher.find()) {
            String option = matcher.group(1).trim();
            if (!option.isEmpty()) {
                options.add(option);
            }
        }

        return options;
    }
}
