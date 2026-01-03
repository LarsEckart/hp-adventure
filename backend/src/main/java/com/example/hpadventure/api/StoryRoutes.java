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
            if (isRateLimited(rateLimiter, ctx.ip())) {
                logger.warn("Story request rate limited requestId={} ip={}", requestId, ctx.ip());
                ctx.status(429).json(Dtos.errorResponse("RATE_LIMITED", "Zu viele Anfragen. Bitte warte kurz.", requestId));
                return;
            }
            Dtos.StoryRequest request = parseRequest(
                () -> ctx.bodyAsClass(Dtos.StoryRequest.class),
                "Story request",
                requestId,
                error -> ctx.status(400).json(error));
            if (request == null) {
                return;
            }
            RequestMeta meta = requestMeta(request);

            logRequestReceived("Story request received", requestId, ctx.ip(), meta);

            if (!validateAction(meta, "Story request missing action", requestId, ctx.ip(),
                error -> ctx.status(400).json(error))) {
                return;
            }

            try {
                Dtos.Assistant assistant = storyHandler.nextTurn(request);
                ctx.json(new Dtos.StoryResponse(assistant));
            } catch (UpstreamException e) {
                logger.warn("Story request upstream failure requestId={} code={} status={} message={}",
                    requestId, e.code(), e.status(), e.getMessage());
                int status = e.status() >= 400 ? e.status() : 502;
                ctx.status(status).json(Dtos.errorResponse(e.code(), "Upstream error: " + e.getMessage(), requestId));
            } catch (Exception e) {
                logger.error("Story request unexpected failure requestId={}", requestId, e);
                ctx.status(500).json(Dtos.errorResponse("INTERNAL_ERROR", "Unexpected server error", requestId));
            }
        });

        if (storyHandler instanceof StoryStreamHandler streamHandler) {
            app.post("/api/story/stream", new SseHandler(client -> {
                String requestId = UUID.randomUUID().toString();
                client.ctx().header("X-Request-Id", requestId);
                if (isRateLimited(rateLimiter, client.ctx().ip())) {
                    logger.warn("Story stream request rate limited requestId={} ip={}", requestId, client.ctx().ip());
                    client.sendEvent("error", Dtos.errorResponse("RATE_LIMITED", "Zu viele Anfragen. Bitte warte kurz.", requestId));
                    client.close();
                    return;
                }
                Dtos.StoryRequest request = parseRequest(
                    () -> client.ctx().bodyAsClass(Dtos.StoryRequest.class),
                    "Story stream request",
                    requestId,
                    error -> {
                        client.sendEvent("error", error);
                        client.close();
                    });
                if (request == null) {
                    return;
                }
                RequestMeta meta = requestMeta(request);

                logRequestReceived("Story stream request received", requestId, client.ctx().ip(), meta);

                if (!validateAction(meta, "Story stream request missing action", requestId, client.ctx().ip(),
                    error -> {
                        client.sendEvent("error", error);
                        client.close();
                    })) {
                    return;
                }

                try {
                    StoryStreamHandler.StreamResult result = streamHandler.streamTurn(request, delta -> {
                        if (delta == null || delta.isEmpty()) {
                            return;
                        }
                        client.sendEvent("delta", new Dtos.StreamDelta(delta));
                    });
                    client.sendEvent("final_text", new Dtos.StoryResponse(result.assistant()));

                    try {
                        Dtos.Image image = streamHandler.generateImage(result.imagePrompt());
                        client.sendEvent("image", new Dtos.StreamImage(image));
                    } catch (UpstreamException e) {
                        logger.warn("Story image request upstream failure requestId={} code={} status={} message={}",
                            requestId, e.code(), e.status(), e.getMessage());
                        client.sendEvent("image_error",
                            Dtos.errorResponse(e.code(), "Illustration konnte nicht geladen werden.", requestId));
                    } catch (Exception e) {
                        logger.error("Story image request unexpected failure requestId={}", requestId, e);
                        client.sendEvent("image_error",
                            Dtos.errorResponse("INTERNAL_ERROR", "Illustration konnte nicht geladen werden.", requestId));
                    }
                } catch (UpstreamException e) {
                    logger.warn("Story stream request upstream failure requestId={} code={} status={} message={}",
                        requestId, e.code(), e.status(), e.getMessage());
                    client.sendEvent("error", Dtos.errorResponse(e.code(), "Upstream error: " + e.getMessage(), requestId));
                } catch (Exception e) {
                    logger.error("Story stream request unexpected failure requestId={}", requestId, e);
                    client.sendEvent("error", Dtos.errorResponse("INTERNAL_ERROR", "Unexpected server error", requestId));
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

    private static boolean isRateLimited(RateLimiter rateLimiter, String ip) {
        return rateLimiter != null && !rateLimiter.allow(ip);
    }

    private static void logRequestReceived(String message, String requestId, String ip, RequestMeta meta) {
        logger.info("{} requestId={} ip={} history={} actionLength={}",
            message, requestId, ip, meta.historySize(), meta.actionLength());
    }

    private static void logMissingAction(String message, String requestId, String ip) {
        logger.warn("{} requestId={} ip={}", message, requestId, ip);
    }

    private static RequestMeta requestMeta(Dtos.StoryRequest request) {
        String action = request == null ? null : request.action();
        return new RequestMeta(action, historySize(request));
    }

    private static boolean isActionMissing(RequestMeta meta) {
        return meta.action() == null || meta.action().isBlank();
    }

    private static Dtos.StoryRequest parseRequest(RequestReader reader, String logPrefix, String requestId,
                                                  ErrorResponder errorResponder) {
        try {
            return reader.read();
        } catch (Exception e) {
            logger.warn("{} invalid body requestId={}", logPrefix, requestId, e);
            errorResponder.respond(Dtos.errorResponse("INVALID_REQUEST", "Invalid JSON body", requestId));
            return null;
        }
    }

    private static boolean validateAction(RequestMeta meta, String logMessage, String requestId, String ip,
                                          ErrorResponder errorResponder) {
        if (isActionMissing(meta)) {
            logMissingAction(logMessage, requestId, ip);
            errorResponder.respond(Dtos.errorResponse("INVALID_REQUEST", "action is required", requestId));
            return false;
        }
        return true;
    }

    @FunctionalInterface
    private interface RequestReader {
        Dtos.StoryRequest read();
    }

    @FunctionalInterface
    private interface ErrorResponder {
        void respond(Dtos.ErrorResponse error);
    }

    private record RequestMeta(String action, int historySize) {
        private int actionLength() {
            return action == null ? 0 : action.length();
        }
    }
}
