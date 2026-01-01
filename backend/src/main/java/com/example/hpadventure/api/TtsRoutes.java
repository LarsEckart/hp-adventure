package com.example.hpadventure.api;

import com.example.hpadventure.services.TtsHandler;
import com.example.hpadventure.services.UpstreamException;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.UUID;

public final class TtsRoutes {
    private static final Logger logger = LoggerFactory.getLogger(TtsRoutes.class);

    private TtsRoutes() {
    }

    public static void register(Javalin app, TtsHandler ttsHandler) {
        app.post("/api/tts", ctx -> {
            String requestId = UUID.randomUUID().toString();
            ctx.header("X-Request-Id", requestId);

            Dtos.TtsRequest request;
            try {
                request = ctx.bodyAsClass(Dtos.TtsRequest.class);
            } catch (Exception e) {
                logger.warn("TTS request invalid body requestId={} ip={}", requestId, ctx.ip(), e);
                ctx.status(400).json(errorResponse("INVALID_REQUEST", "Invalid JSON body", requestId));
                return;
            }

            String text = request == null ? null : request.text();
            int textLength = safeLength(text);
            logger.info("TTS request received requestId={} ip={} textLength={}", requestId, ctx.ip(), textLength);

            if (text == null || text.isBlank()) {
                logger.warn("TTS request missing text requestId={} ip={}", requestId, ctx.ip());
                ctx.status(400).json(errorResponse("INVALID_REQUEST", "text is required", requestId));
                return;
            }

            try {
                ctx.contentType("audio/mpeg");
                ctx.status(200);
                OutputStream outputStream = ctx.outputStream();
                ttsHandler.stream(text, outputStream);
            } catch (UpstreamException e) {
                logger.warn("TTS request upstream failure requestId={} code={} status={} message={}",
                    requestId, e.code(), e.status(), e.getMessage());
                if (!ctx.res().isCommitted()) {
                    int status = e.status() >= 400 ? e.status() : 502;
                    ctx.status(status).json(errorResponse(e.code(), "Upstream error: " + e.getMessage(), requestId));
                }
            } catch (Exception e) {
                logger.error("TTS request unexpected failure requestId={}", requestId, e);
                if (!ctx.res().isCommitted()) {
                    ctx.status(500).json(errorResponse("INTERNAL_ERROR", "Unexpected server error", requestId));
                }
            }
        });
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static Dtos.ErrorResponse errorResponse(String code, String message, String requestId) {
        return new Dtos.ErrorResponse(new Dtos.ErrorResponse.Error(code, message, requestId));
    }
}
