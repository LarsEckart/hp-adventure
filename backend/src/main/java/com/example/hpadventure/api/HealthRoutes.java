package com.example.hpadventure.api;

import io.javalin.Javalin;

public final class HealthRoutes {
    private HealthRoutes() {
    }

    public static void register(Javalin app) {
        app.get("/health", ctx -> ctx.result("ok"));
    }
}
