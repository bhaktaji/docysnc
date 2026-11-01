package com.docsync.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Tracks GitHub API rate-limit headers and refuses non-critical calls when the remaining
 * quota drops below 10% (FR-065).
 *
 * <p>Headers are recorded after every GitHub response. Critical calls (installation-token
 * creation) always proceed, because they are required to serve an in-flight onboarding
 * request. Non-critical calls fail with {@link RateLimitedException} until the reset
 * timestamp, where the API returns {@code 503} with {@code Retry-After}. Automatic retry
 * of deferred work belongs to the M3 job queue.</p>
 *
 * <p>The tracker is in-memory and process-local; a multi-instance deployment would share
 * the quota but not this state. The guard therefore errs on the side of refusing too
 * early, and GitHub's own {@code 403}/{@code 429} remains the final authority.</p>
 */
@Component
public class GitHubRateLimitGuard {

    private static final Logger log = LoggerFactory.getLogger(GitHubRateLimitGuard.class);

    private static final double DEFAULT_THRESHOLD = 0.10;

    private final Clock clock;
    private final double threshold;
    private volatile Snapshot snapshot = Snapshot.NONE;

    public GitHubRateLimitGuard(Clock clock,
                                @Value("${docsync.github.rate-limit-warning-threshold:0.10}")
                                double threshold) {
        this.clock = clock;
        this.threshold = threshold > 0 && threshold <= 1 ? threshold : DEFAULT_THRESHOLD;
    }

    /**
     * Records the rate-limit headers of a GitHub response. Missing or malformed headers
     * leave the previous snapshot unchanged.
     */
    public void record(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        Long remaining = longHeader(headers, "X-RateLimit-Remaining");
        Long limit = longHeader(headers, "X-RateLimit-Limit");
        Long reset = longHeader(headers, "X-RateLimit-Reset");
        if (remaining == null || reset == null) {
            return;
        }
        snapshot = new Snapshot(remaining, limit == null ? 0 : limit, Instant.ofEpochSecond(reset));
    }

    /** Always allowed: token creation is required to serve an in-flight request. */
    public void checkCritical() {
        // No throttling for critical calls.
    }

    /**
     * Refuses the call when the remaining quota is below the threshold and the reset is
     * still in the future.
     *
     * @throws RateLimitedException when the call must be deferred
     */
    public void checkNonCritical() {
        Snapshot current = snapshot;
        if (!isExhausted(current)) {
            return;
        }

        Instant now = clock.instant();
        Duration retryAfter = Duration.between(now, current.resetAt());
        if (retryAfter.isNegative() || retryAfter.isZero()) {
            // The window has already reset; allow the next call and let it refresh headers.
            return;
        }

        log.warn("Deferring non-critical GitHub API call: remaining={} limit={} resetAt={}",
                current.remaining(), current.limit(), current.resetAt());
        throw new RateLimitedException(current.resetAt(), retryAfter);
    }

    /** Visible for tests and metrics. */
    public boolean isThrottled() {
        return isExhausted(snapshot);
    }

    private boolean isExhausted(Snapshot current) {
        if (current.resetAt() == null) {
            return false;
        }
        if (current.limit() <= 0) {
            // When the total is unknown, only a fully exhausted quota is throttled.
            return current.remaining() <= 0;
        }
        long allowed = (long) Math.floor(current.limit() * threshold);
        return current.remaining() < allowed;
    }

    private static Long longHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record Snapshot(long remaining, long limit, Instant resetAt) {
        private static final Snapshot NONE = new Snapshot(-1, 0, null);
    }
}
