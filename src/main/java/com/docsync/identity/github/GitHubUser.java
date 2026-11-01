package com.docsync.identity.github;

/**
 * Minimal GitHub profile data needed to create or link a DocSync user.
 */
public record GitHubUser(
        Long id,
        String login,
        String email,
        String displayName,
        String avatarUrl
) {
}
