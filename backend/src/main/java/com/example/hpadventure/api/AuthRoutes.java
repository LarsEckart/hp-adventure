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

    public sealed interface AuthResult {
        record Valid(String user) implements AuthResult {}
        record Invalid() implements AuthResult {}

        default boolean isValid() {
            return this instanceof Valid;
        }

        default String user() {
            return this instanceof Valid v ? v.user() : null;
        }
    }

    private final Map<String, String> passwordToUser;

    public AuthRoutes(Map<String, String> userToPassword) {
        // Invert the map: we need password -> user for lookups
        this.passwordToUser = invertMap(userToPassword);
        if (passwordToUser.isEmpty()) {
            logger.warn("No APP_PASSWORDS configured - authentication disabled");
        } else {
            logger.info("Configured {} user(s) for authentication", passwordToUser.size());
        }
    }

    private static Map<String, String> invertMap(Map<String, String> map) {
        var result = new HashMap<String, String>();
        map.forEach((key, value) -> result.put(value, key));
        return result;
    }

    public boolean isEnabled() {
        return !passwordToUser.isEmpty();
    }

    public AuthResult validatePassword(String password) {
        if (password == null || password.isBlank()) {
            return new AuthResult.Invalid();
        }
        String user = passwordToUser.get(password);
        return user != null ? new AuthResult.Valid(user) : new AuthResult.Invalid();
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
            AuthResult result = validatePassword(password);
            
            if (!result.isValid()) {
                logger.warn("[AUTH] Unauthorized request to {} from {}", ctx.path(), ctx.ip());
                ctx.status(401).json(new Dtos.ErrorResponse(
                    new Dtos.ErrorResponse.Error("UNAUTHORIZED", "Ungültiges Passwort.", null)
                ));
                return;
            }

            // Store user for logging
            ctx.attribute("authUser", result.user());
            logger.info("[AUTH] {}: {} {}", result.user(), ctx.method(), ctx.path());
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
        AuthResult result = validatePassword(password);

        if (!result.isValid()) {
            logger.info("[AUTH] Failed validation attempt from {}", ctx.ip());
            ctx.status(401).json(new Dtos.ErrorResponse(
                new Dtos.ErrorResponse.Error("UNAUTHORIZED", "Ungültiges Passwort.", null)
            ));
        } else {
            logger.info("[AUTH] {} validated successfully", result.user());
            ctx.status(200).json(Map.of("valid", true));
        }
    }
}
