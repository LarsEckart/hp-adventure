package com.example.hpadventure.api;

import com.example.hpadventure.config.RateLimiter;
import com.example.hpadventure.services.StoryHandler;
import com.example.hpadventure.services.StoryStreamHandler;
import com.example.hpadventure.services.UpstreamException;
import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import io.javalin.http.sse.SseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class StoryRoutes {
    private static final Logger logger = LoggerFactory.getLogger(StoryRoutes.class);
    private static final String IMAGE_FAILURE_MESSAGE = "Illustration konnte nicht geladen werden.";

    private StoryRoutes() {
    }

    public static void register(Javalin app, StoryHandler storyHandler, RateLimiter rateLimiter) {
        registerStoryRequest(app, storyHandler, rateLimiter);
        registerStoryStream(app, storyHandler, rateLimiter);
    }

    private static void registerStoryRequest(Javalin app, StoryHandler storyHandler, RateLimiter rateLimiter) {
        app.post("/api/story", ctx -> {
            String requestId = UUID.randomUUID().toString();
            ctx.header("X-Request-Id", requestId);
            if (rejectIfRateLimited(rateLimiter, ctx.ip(), requestId, "Story request rate limited",
                error -> ctx.status(429).json(error))) {
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
    }

    private static void registerStoryStream(Javalin app, StoryHandler storyHandler, RateLimiter rateLimiter) {
        if (storyHandler instanceof StoryStreamHandler streamHandler) {
            app.post("/api/story/stream", new SseHandler(client -> {
                String requestId = UUID.randomUUID().toString();
                var ctx = client.ctx();
                ctx.header("X-Request-Id", requestId);
                if (rejectIfRateLimited(rateLimiter, ctx.ip(), requestId,
                    "Story stream request rate limited", streamErrorResponder(client))) {
                    return;
                }
                Dtos.StoryRequest request = parseRequest(
                    () -> ctx.bodyAsClass(Dtos.StoryRequest.class),
                    "Story stream request",
                    requestId,
                    streamErrorResponder(client));
                if (request == null) {
                    return;
                }
                RequestMeta meta = requestMeta(request);

                logRequestReceived("Story stream request received", requestId, ctx.ip(), meta);

                if (!validateAction(meta, "Story stream request missing action", requestId, ctx.ip(),
                    streamErrorResponder(client))) {
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

                    sendImage(streamHandler, result, client, requestId);
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

    private static boolean rejectIfRateLimited(RateLimiter rateLimiter, String ip, String requestId,
                                               String logMessage, ErrorResponder errorResponder) {
        if (rateLimiter == null || rateLimiter.allow(ip)) {
            return false;
        }
        logger.warn("{} requestId={} ip={}", logMessage, requestId, ip);
        errorResponder.respond(Dtos.errorResponse("RATE_LIMITED", "Zu viele Anfragen. Bitte warte kurz.", requestId));
        return true;
    }

    private static void logRequestReceived(String message, String requestId, String ip, RequestMeta meta) {
        logger.info("{} requestId={} ip={} history={} actionLength={}",
            message, requestId, ip, meta.historySize(), meta.actionLength());
    }

    private static RequestMeta requestMeta(Dtos.StoryRequest request) {
        String action = request == null ? null : request.action();
        int historySize = request == null || request.conversationHistory() == null
            ? 0
            : request.conversationHistory().size();
        return new RequestMeta(action, historySize);
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
        if (meta.isActionMissing()) {
            logger.warn("{} requestId={} ip={}", logMessage, requestId, ip);
            errorResponder.respond(Dtos.errorResponse("INVALID_REQUEST", "action is required", requestId));
            return false;
        }
        return true;
    }

    private static ErrorResponder streamErrorResponder(SseClient client) {
        return error -> {
            client.sendEvent("error", error);
            client.close();
        };
    }

    private static void sendImageError(SseClient client, String requestId, String code, String message) {
        client.sendEvent("image_error", Dtos.errorResponse(code, message, requestId));
    }

    private static void sendImage(StoryStreamHandler streamHandler, StoryStreamHandler.StreamResult result,
                                  SseClient client, String requestId) {
        try {
            Dtos.Image image = streamHandler.generateImage(result.imagePrompt());
            client.sendEvent("image", new Dtos.StreamImage(image));
        } catch (UpstreamException e) {
            logger.warn("Story image request upstream failure requestId={} code={} status={} message={}",
                requestId, e.code(), e.status(), e.getMessage());
            sendImageError(client, requestId, e.code(), IMAGE_FAILURE_MESSAGE);
        } catch (Exception e) {
            logger.error("Story image request unexpected failure requestId={}", requestId, e);
            sendImageError(client, requestId, "INTERNAL_ERROR", IMAGE_FAILURE_MESSAGE);
        }
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

        private boolean isActionMissing() {
            return action == null || action.isBlank();
        }
    }
}
