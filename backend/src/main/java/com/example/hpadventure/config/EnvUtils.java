package com.example.hpadventure.config;

/**
 * Utility methods for parsing environment variables.
 */
public final class EnvUtils {
    private EnvUtils() {
    }

    /**
     * Parse a string as an integer, returning null if blank or invalid.
     */
    public static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a string as an integer, returning a default value if blank or invalid.
     */
    public static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
