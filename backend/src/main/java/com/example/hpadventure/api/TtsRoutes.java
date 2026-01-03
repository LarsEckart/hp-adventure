package com.example.hpadventure.api;

import com.example.hpadventure.services.TtsHandler;
import com.example.hpadventure.services.UpstreamException;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterOutputStream;
import java.io.IOException;
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
                ctx.status(400).json(Dtos.errorResponse("INVALID_REQUEST", "Invalid JSON body", requestId));
                return;
            }

            String text = request == null ? null : request.text();
            int textLength = safeLength(text);
            logger.info("TTS request received requestId={} ip={} textLength={}", requestId, ctx.ip(), textLength);

            if (text == null || text.isBlank()) {
                logger.warn("TTS request missing text requestId={} ip={}", requestId, ctx.ip());
                ctx.status(400).json(Dtos.errorResponse("INVALID_REQUEST", "text is required", requestId));
                return;
            }

            try {
                ctx.contentType("audio/mpeg");
                ctx.status(200);
                long startedAt = System.nanoTime();
                CountingOutputStream outputStream = new CountingOutputStream(ctx.outputStream());
                ttsHandler.stream(text, outputStream);
                outputStream.flush();
                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                logger.info("TTS request completed requestId={} ip={} bytes={} durationMs={}",
                    requestId, ctx.ip(), outputStream.bytesWritten(), durationMs);
            } catch (UpstreamException e) {
                logger.warn("TTS request upstream failure requestId={} code={} status={} message={}",
                    requestId, e.code(), e.status(), e.getMessage());
                if (!ctx.res().isCommitted()) {
                    int status = e.status() >= 400 ? e.status() : 502;
                    ctx.status(status).json(Dtos.errorResponse(e.code(), "Upstream error: " + e.getMessage(), requestId));
                }
            } catch (Exception e) {
                logger.error("TTS request unexpected failure requestId={}", requestId, e);
                if (!ctx.res().isCommitted()) {
                    ctx.status(500).json(Dtos.errorResponse("INTERNAL_ERROR", "Unexpected server error", requestId));
                }
            }
        });
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        private CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            super.write(b);
            count += 1;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            super.write(b, off, len);
            count += len;
        }

        private long bytesWritten() {
            return count;
        }
    }
}
