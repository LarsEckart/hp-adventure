package com.example.hpadventure.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configured users who can access the app.
 * 
 * Parse from "user:password,user2:password2" format.
 */
public final class Users {

    private final Map<String, String> passwordToUser;

    private Users(Map<String, String> passwordToUser) {
        this.passwordToUser = passwordToUser;
    }

    public static Users parse(String config) {
        var passwordToUser = new HashMap<String, String>();
        if (config == null || config.isBlank()) {
            return new Users(passwordToUser);
        }
        for (String entry : config.split(",")) {
            String trimmed = entry.trim();
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex > 0 && colonIndex < trimmed.length() - 1) {
                String user = trimmed.substring(0, colonIndex).trim();
                String password = trimmed.substring(colonIndex + 1).trim();
                if (!user.isEmpty() && !password.isEmpty()) {
                    passwordToUser.put(password, user);
                }
            }
        }
        return new Users(passwordToUser);
    }

    public int count() {
        return passwordToUser.size();
    }

    public Optional<String> findByPassword(String password) {
        if (password == null || password.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(passwordToUser.get(password));
    }
}
