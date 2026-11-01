package com.docsync.github;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubRateLimitGuardTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    @Test
    void unknownQuotaDoesNotThrottle() {
        GitHubRateLimitGuard guard = guard();
        assertFalse(guard.isThrottled());
        assertDoesNotThrow(guard::checkNonCritical);
    }

    @Test
    void healthyQuotaAllowsCalls() {
        GitHubRateLimitGuard guard = guard();
        guard.record(headers(4000, 5000, NOW.plusSeconds(600)));

        assertFalse(guard.isThrottled());
        assertDoesNotThrow(guard::checkNonCritical);
    }

    @Test
    void belowTenPercentDefersNonCriticalCallsUntilReset() {
        GitHubRateLimitGuard guard = guard();
        guard.record(headers(400, 5000, NOW.plusSeconds(120)));

        assertTrue(guard.isThrottled());
        RateLimitedException error = assertThrows(RateLimitedException.class, guard::checkNonCritical);
        assertEquals("ERR_GITHUB_RATE_LIMITED", error.getErrorCode());
        assertEquals(NOW.plusSeconds(120), error.getRetryAt());
        assertDoesNotThrow(guard::checkCritical);
    }

    @Test
    void exactlyTenPercentIsNotThrottled() {
        GitHubRateLimitGuard guard = guard();
        guard.record(headers(500, 5000, NOW.plusSeconds(120)));

        assertFalse(guard.isThrottled());
        assertDoesNotThrow(guard::checkNonCritical);
    }

    @Test
    void resetInThePastAllowsTheNextCall() {
        GitHubRateLimitGuard guard = guard();
        guard.record(headers(0, 5000, NOW.minusSeconds(1)));

        assertDoesNotThrow(guard::checkNonCritical);
    }

    @Test
    void malformedOrMissingHeadersAreIgnored() {
        GitHubRateLimitGuard guard = guard();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "not-a-number");
        guard.record(headers);
        assertFalse(guard.isThrottled());

        guard.record(headers(1, 5000, NOW.plusSeconds(60)));
        assertTrue(guard.isThrottled());

        // A later response without rate-limit headers must not clear the known state.
        guard.record(new HttpHeaders());
        assertTrue(guard.isThrottled());
    }

    private static GitHubRateLimitGuard guard() {
        return new GitHubRateLimitGuard(Clock.fixed(NOW, ZoneOffset.UTC), 0.10);
    }

    private static HttpHeaders headers(long remaining, long limit, Instant reset) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", String.valueOf(remaining));
        headers.set("X-RateLimit-Limit", String.valueOf(limit));
        headers.set("X-RateLimit-Reset", String.valueOf(reset.getEpochSecond()));
        return headers;
    }
}
