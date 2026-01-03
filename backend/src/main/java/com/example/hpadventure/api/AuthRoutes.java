package com.example.hpadventure.api;

import com.example.hpadventure.auth.UserRepository;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public final class AuthRoutes {
    private static final Logger logger = LoggerFactory.getLogger(AuthRoutes.class);
    private static final String PASSWORD_HEADER = "X-App-Password";

    private final UserRepository userRepository;

    public AuthRoutes(UserRepository userRepository) {
        this.userRepository = userRepository;
        logger.info("Configured {} user(s) for authentication", userRepository.userCount());
    }

    /**
     * Middleware that checks X-App-Password header
     */
    public Handler authMiddleware() {
        return ctx -> {
            String password = ctx.header(PASSWORD_HEADER);
            Optional<String> user = userRepository.authenticate(password);

            if (user.isEmpty()) {
                logger.warn("[AUTH] Unauthorized request to {} from {}", ctx.path(), ctx.ip());
                ctx.status(401).json(new Dtos.ErrorResponse(
                    new Dtos.ErrorResponse.Error("UNAUTHORIZED", "Ungültiges Passwort.", null)
                ));
                return;
            }

            // Store user for logging
            ctx.attribute("authUser", user.get());
            logger.info("[AUTH] {}: {} {}", user.get(), ctx.method(), ctx.path());
        };
    }

    /**
     * Register /api/auth/validate endpoint
     */
    public void register(Javalin app) {
        app.post("/api/auth/validate", this::handleValidate);
    }

    private void handleValidate(Context ctx) {
        String password = ctx.header(PASSWORD_HEADER);
        Optional<String> user = userRepository.authenticate(password);

        if (user.isEmpty()) {
            logger.info("[AUTH] Failed validation attempt from {}", ctx.ip());
            ctx.status(401).json(new Dtos.ErrorResponse(
                new Dtos.ErrorResponse.Error("UNAUTHORIZED", "Ungültiges Passwort.", null)
            ));
        } else {
            logger.info("[AUTH] {} validated successfully", user.get());
            ctx.status(200).json(Map.of("valid", true));
        }
    }
}
