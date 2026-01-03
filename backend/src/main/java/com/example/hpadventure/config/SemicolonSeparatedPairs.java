package com.example.hpadventure.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses "key:value,key2:value2" format into a map.
 */
public final class SemicolonSeparatedPairs {
    private final String config;

    private SemicolonSeparatedPairs(String config) {
        this.config = config;
    }

    public static SemicolonSeparatedPairs from(String config) {
        return new SemicolonSeparatedPairs(config);
    }

    public Map<String, String> toMap() {
        Map<String, String> result = new HashMap<>();
        if (config == null || config.isBlank()) {
            return result;
        }
        for (String entry : config.split(",")) {
            String trimmed = entry.trim();
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex > 0 && colonIndex < trimmed.length() - 1) {
                String key = trimmed.substring(0, colonIndex).trim();
                String value = trimmed.substring(colonIndex + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }
}
