package com.docsync.github.dto;

import java.time.Instant;

/**
 * A GitHub App installation access token. Never persisted or logged.
 */
public record InstallationToken(String token, Instant expiresAt) {

    public InstallationToken {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Installation token must not be blank");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Installation token expiry must be present");
        }
    }

    public boolean isUsableAt(Instant instant) {
        return expiresAt.isAfter(instant);
    }
}
