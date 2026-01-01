package com.example.hpadventure.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {
    @Test
    void blocksWhenOverLimit() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter limiter = new RateLimiter(clock, 2, Duration.ofMinutes(1));

        assertTrue(limiter.allow("127.0.0.1"));
        assertTrue(limiter.allow("127.0.0.1"));
        assertFalse(limiter.allow("127.0.0.1"));
    }

    @Test
    void refillsAfterWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter limiter = new RateLimiter(clock, 1, Duration.ofMinutes(1));

        assertTrue(limiter.allow("ip"));
        assertFalse(limiter.allow("ip"));

        clock.advance(Duration.ofMinutes(1));
        assertTrue(limiter.allow("ip"));
    }

    @Test
    void separatesBucketsByKey() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter limiter = new RateLimiter(clock, 1, Duration.ofMinutes(1));

        assertTrue(limiter.allow("ip-a"));
        assertTrue(limiter.allow("ip-b"));
        assertFalse(limiter.allow("ip-a"));
        assertFalse(limiter.allow("ip-b"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
