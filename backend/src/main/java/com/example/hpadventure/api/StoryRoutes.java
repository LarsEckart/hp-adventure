package com.example.hpadventure.api;

import com.example.hpadventure.config.RateLimiter;
import com.example.hpadventure.services.StoryHandler;
import com.example.hpadventure.services.StoryStreamHandler;
import com.example.hpadventure.services.UpstreamException;
import io.javalin.Javalin;
import io.javalin.http.sse.SseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class StoryRoutes {
    private static final Logger logger = LoggerFactory.getLogger(StoryRoutes.class);

    private StoryRoutes() {
    }

    public static void register(Javalin app, StoryHandler storyHandler, RateLimiter rateLimiter) {
        app.post("/api/story", ctx -> {
            String requestId = UUID.randomUUID().toString();
            ctx.header("X-Request-Id", requestId);
            if (rateLimiter != null && !rateLimiter.allow(ctx.ip())) {
                logger.warn("Story request rate limited requestId={} ip={}", requestId, ctx.ip());
                ctx.status(429).json(errorResponse("RATE_LIMITED", "Zu viele Anfragen. Bitte warte kurz.", requestId));
                return;
            }
            Dtos.StoryRequest request;
            try {
                request = ctx.bodyAsClass(Dtos.StoryRequest.class);
            } catch (Exception e) {
                logger.warn("Story request invalid body requestId={} ip={}", requestId, ctx.ip(), e);
                ctx.status(400).json(errorResponse("INVALID_REQUEST", "Invalid JSON body", requestId));
                return;
            }
            String action = request == null ? null : request.action();
            int historySize = historySize(request);
            int actionLength = safeLength(action);

            logger.info("Story request received requestId={} ip={} history={} actionLength={}",
                requestId, ctx.ip(), historySize, actionLength);

            if (action == null || action.isBlank()) {
                logger.warn("Story request missing action requestId={} ip={}", requestId, ctx.ip());
                ctx.status(400).json(errorResponse("INVALID_REQUEST", "action is required", requestId));
                return;
            }

            try {
                Dtos.Assistant assistant = storyHandler.nextTurn(request);
                ctx.json(new Dtos.StoryResponse(assistant));
            } catch (UpstreamException e) {
                logger.warn("Story request upstream failure requestId={} code={} status={} message={}",
                    requestId, e.code(), e.status(), e.getMessage());
                int status = e.status() >= 400 ? e.status() : 502;
                ctx.status(status).json(errorResponse(e.code(), "Upstream error: " + e.getMessage(), requestId));
            } catch (Exception e) {
                logger.error("Story request unexpected failure requestId={}", requestId, e);
                ctx.status(500).json(errorResponse("INTERNAL_ERROR", "Unexpected server error", requestId));
            }
        });

        if (storyHandler instanceof StoryStreamHandler streamHandler) {
            app.post("/api/story/stream", new SseHandler(client -> {
                String requestId = UUID.randomUUID().toString();
                client.ctx().header("X-Request-Id", requestId);
                if (rateLimiter != null && !rateLimiter.allow(client.ctx().ip())) {
                    logger.warn("Story stream request rate limited requestId={} ip={}", requestId, client.ctx().ip());
                    client.sendEvent("error", errorResponse("RATE_LIMITED", "Zu viele Anfragen. Bitte warte kurz.", requestId));
                    client.close();
                    return;
                }
                Dtos.StoryRequest request;
                try {
                    request = client.ctx().bodyAsClass(Dtos.StoryRequest.class);
                } catch (Exception e) {
                    logger.warn("Story stream request invalid body requestId={} ip={}", requestId, client.ctx().ip(), e);
                    client.sendEvent("error", errorResponse("INVALID_REQUEST", "Invalid JSON body", requestId));
                    client.close();
                    return;
                }
                String action = request == null ? null : request.action();
                int historySize = historySize(request);
                int actionLength = safeLength(action);

                logger.info("Story stream request received requestId={} ip={} history={} actionLength={}",
                    requestId, client.ctx().ip(), historySize, actionLength);

                if (action == null || action.isBlank()) {
                    logger.warn("Story stream request missing action requestId={} ip={}", requestId, client.ctx().ip());
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
                    logger.warn("Story stream request upstream failure requestId={} code={} status={} message={}",
                        requestId, e.code(), e.status(), e.getMessage());
                    client.sendEvent("error", errorResponse(e.code(), "Upstream error: " + e.getMessage(), requestId));
                } catch (Exception e) {
                    logger.error("Story stream request unexpected failure requestId={}", requestId, e);
                    client.sendEvent("error", errorResponse("INTERNAL_ERROR", "Unexpected server error", requestId));
                } finally {
                    client.close();
                }
            }));
        }
    }

    private static int historySize(Dtos.StoryRequest request) {
        if (request == null || request.conversationHistory() == null) {
            return 0;
        }
        return request.conversationHistory().size();
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static Dtos.ErrorResponse errorResponse(String code, String message, String requestId) {
        return new Dtos.ErrorResponse(new Dtos.ErrorResponse.Error(code, message, requestId));
    }
}
