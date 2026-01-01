package com.example.hpadventure.api;

import com.example.hpadventure.config.RateLimiter;
import com.example.hpadventure.services.StoryHandler;
import com.example.hpadventure.services.StoryStreamHandler;
import com.example.hpadventure.services.UpstreamException;
import io.javalin.Javalin;
import io.javalin.http.sse.SseHandler;

import java.util.UUID;

public final class StoryRoutes {
    private StoryRoutes() {
    }

    public static void register(Javalin app, StoryHandler storyHandler, RateLimiter rateLimiter) {
        app.post("/api/story", ctx -> {
            String requestId = UUID.randomUUID().toString();
            if (rateLimiter != null && !rateLimiter.allow(ctx.ip())) {
                ctx.status(429).json(errorResponse("RATE_LIMITED", "Zu viele Anfragen. Bitte warte kurz.", requestId));
                return;
            }
            Dtos.StoryRequest request = ctx.bodyAsClass(Dtos.StoryRequest.class);
            String action = request == null ? null : request.action();

            if (action == null || action.isBlank()) {
                ctx.status(400).json(errorResponse("INVALID_REQUEST", "action is required", requestId));
                return;
            }

            try {
                Dtos.Assistant assistant = storyHandler.nextTurn(request);
                ctx.json(new Dtos.StoryResponse(assistant));
            } catch (UpstreamException e) {
                int status = e.status() >= 400 ? e.status() : 502;
                ctx.status(status).json(errorResponse(e.code(), "Upstream error: " + e.getMessage(), requestId));
            } catch (Exception e) {
                ctx.status(500).json(errorResponse("INTERNAL_ERROR", "Unexpected server error", requestId));
            }
        });

        if (storyHandler instanceof StoryStreamHandler streamHandler) {
            app.post("/api/story/stream", new SseHandler(client -> {
                String requestId = UUID.randomUUID().toString();
                if (rateLimiter != null && !rateLimiter.allow(client.ctx().ip())) {
                    client.sendEvent("error", errorResponse("RATE_LIMITED", "Zu viele Anfragen. Bitte warte kurz.", requestId));
                    client.close();
                    return;
                }
                Dtos.StoryRequest request = client.ctx().bodyAsClass(Dtos.StoryRequest.class);
                String action = request == null ? null : request.action();

                if (action == null || action.isBlank()) {
                    client.sendEvent("error", errorResponse("INVALID_REQUEST", "action is required", requestId));
                    client.close();
                    return;
                }

                try {
                    Dtos.Assistant assistant = streamHandler.streamTurn(request, delta -> {
                        if (delta == null || delta.isBlank()) {
                            return;
                        }
                        client.sendEvent("delta", new Dtos.StreamDelta(delta));
                    });
                    client.sendEvent("final", new Dtos.StoryResponse(assistant));
                } catch (UpstreamException e) {
                    client.sendEvent("error", errorResponse(e.code(), "Upstream error: " + e.getMessage(), requestId));
                } catch (Exception e) {
                    client.sendEvent("error", errorResponse("INTERNAL_ERROR", "Unexpected server error", requestId));
                } finally {
                    client.close();
                }
            }));
        }
    }

    private static Dtos.ErrorResponse errorResponse(String code, String message, String requestId) {
        return new Dtos.ErrorResponse(new Dtos.ErrorResponse.Error(code, message, requestId));
    }
}
