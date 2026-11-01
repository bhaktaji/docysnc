package com.docsync.identity.github;

/**
 * Exchanges a GitHub OAuth authorization code for an authenticated profile.
 */
public interface GitHubOAuthProvider {

    GitHubUser exchangeCode(String code);
}
