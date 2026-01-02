package com.example.hpadventure.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class AuthRoutes {
    private static final Logger logger = LoggerFactory.getLogger(AuthRoutes.class);
    private static final String PASSWORD_HEADER = "X-App-Password";

    private final Map<String, String> passwordToUser;

    public AuthRoutes(String passwordsConfig) {
        this.passwordToUser = parsePasswords(passwordsConfig);
        if (passwordToUser.isEmpty()) {
            logger.warn("No APP_PASSWORDS configured - authentication disabled");
        } else {
            logger.info("Configured {} user(s) for authentication", passwordToUser.size());
        }
    }

    /**
     * Parse "name:password,name2:password2" format into password->user map
     */
    private static Map<String, String> parsePasswords(String config) {
        Map<String, String> result = new HashMap<>();
        if (config == null || config.isBlank()) {
            return result;
        }
        for (String entry : config.split(",")) {
            String trimmed = entry.trim();
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex > 0 && colonIndex < trimmed.length() - 1) {
                String user = trimmed.substring(0, colonIndex).trim();
                String password = trimmed.substring(colonIndex + 1).trim();
                if (!user.isEmpty() && !password.isEmpty()) {
                    result.put(password, user);
                }
            }
        }
        return result;
    }

    public boolean isEnabled() {
        return !passwordToUser.isEmpty();
    }

    /**
     * Validate password and return username, or null if invalid
     */
    public String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            return null;
        }
        return passwordToUser.get(password);
    }

    /**
     * Middleware that checks X-App-Password header
     */
    public Handler authMiddleware() {
        return ctx -> {
            if (!isEnabled()) {
                return; // No passwords configured, allow all
            }

            String password = ctx.header(PASSWORD_HEADER);
            String user = validatePassword(password);
            
            if (user == null) {
                logger.warn("[AUTH] Unauthorized request to {} from {}", ctx.path(), ctx.ip());
                ctx.status(401).json(new Dtos.ErrorResponse(
                    new Dtos.ErrorResponse.Error("UNAUTHORIZED", "Ungültiges Passwort.", null)
                ));
                return;
            }

            // Store user for logging
            ctx.attribute("authUser", user);
            logger.info("[AUTH] {}: {} {}", user, ctx.method(), ctx.path());
        };
    }

    /**
     * Register /api/auth/validate endpoint
     */
    public void register(Javalin app) {
        app.post("/api/auth/validate", this::handleValidate);
    }

    private void handleValidate(Context ctx) {
        if (!isEnabled()) {
            // No passwords configured, always valid
            ctx.status(200).json(Map.of("valid", true));
            return;
        }

        String password = ctx.header(PASSWORD_HEADER);
        String user = validatePassword(password);

        if (user == null) {
            logger.info("[AUTH] Failed validation attempt from {}", ctx.ip());
            ctx.status(401).json(new Dtos.ErrorResponse(
                new Dtos.ErrorResponse.Error("UNAUTHORIZED", "Ungültiges Passwort.", null)
            ));
        } else {
            logger.info("[AUTH] {} validated successfully", user);
            ctx.status(200).json(Map.of("valid", true));
        }
    }
}
