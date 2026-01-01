package com.example.hpadventure.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
    private final Clock clock;
    private final int maxTokens;
    private final Duration refillWindow;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock, int maxTokens, Duration refillWindow) {
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (refillWindow == null || refillWindow.isZero() || refillWindow.isNegative()) {
            throw new IllegalArgumentException("refillWindow must be positive");
        }
        this.clock = clock;
        this.maxTokens = maxTokens;
        this.refillWindow = refillWindow;
    }

    public boolean allow(String key) {
        String normalized = (key == null || key.isBlank()) ? "unknown" : key;
        Bucket bucket = buckets.computeIfAbsent(normalized, value -> new Bucket(maxTokens, clock.instant()));
        synchronized (bucket) {
            bucket.refillIfNeeded(clock.instant(), maxTokens, refillWindow);
            if (bucket.tokens <= 0) {
                return false;
            }
            bucket.tokens -= 1;
            return true;
        }
    }

    private static final class Bucket {
        private int tokens;
        private Instant lastRefill;

        private Bucket(int tokens, Instant lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }

        private void refillIfNeeded(Instant now, int maxTokens, Duration window) {
            if (Duration.between(lastRefill, now).compareTo(window) >= 0) {
                tokens = maxTokens;
                lastRefill = now;
            }
        }
    }
}
