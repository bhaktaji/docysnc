package com.docsync.github;

import com.docsync.common.exception.DocSyncException;

import java.time.Duration;
import java.time.Instant;

/**
 * Raised when a non-critical GitHub API call is refused because the rate limit is nearly
 * exhausted. The caller may retry after {@link #getRetryAt()}.
 */
public class RateLimitedException extends DocSyncException {

    private final Instant retryAt;

    public RateLimitedException(Instant retryAt, Duration retryAfter) {
        super("ERR_GITHUB_RATE_LIMITED",
                String.format("GitHub API rate limit is nearly exhausted; retry after %d seconds",
                        Math.max(1, retryAfter.toSeconds())));
        this.retryAt = retryAt;
    }

    public Instant getRetryAt() {
        return retryAt;
    }
}
