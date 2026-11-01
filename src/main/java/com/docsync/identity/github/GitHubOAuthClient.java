package com.docsync.identity.github;

import com.docsync.common.exception.ExternalServiceException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * GitHub OAuth 2.0 client. Only the minimum scopes ({@code read:user}, {@code user:email})
 * are used. Client credentials are sourced from environment-backed configuration and are
 * never logged.
 */
@Component
public class GitHubOAuthClient implements GitHubOAuthProvider {

    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String EMAILS_URL = "https://api.github.com/user/emails";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public GitHubOAuthClient(RestClient.Builder builder,
                             @Value("${docsync.github.oauth.client-id:}") String clientId,
                             @Value("${docsync.github.oauth.client-secret:}") String clientSecret) {
        this.restClient = builder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public GitHubUser exchangeCode(String code) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ExternalServiceException("GitHub", "GitHub OAuth is not configured");
        }

        TokenResponse token = requestAccessToken(code);
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            String reason = token == null ? "empty response" : token.error();
            throw new ExternalServiceException("GitHub", "OAuth exchange rejected: " + reason);
        }

        ProfileResponse profile = requestProfile(token.accessToken());
        if (profile == null || profile.id() == null) {
            throw new ExternalServiceException("GitHub", "OAuth profile response was invalid");
        }

        String email = profile.email();
        if (email == null || email.isBlank()) {
            email = primaryVerifiedEmail(token.accessToken());
        }
        if (email == null || email.isBlank()) {
            throw new ExternalServiceException("GitHub",
                    "No verified email address is available for this GitHub account");
        }

        String displayName = profile.name() == null || profile.name().isBlank()
                ? profile.login()
                : profile.name();

        return new GitHubUser(profile.id(), profile.login(), email, displayName, profile.avatarUrl());
    }

    private TokenResponse requestAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);

        try {
            return restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("GitHub", "OAuth token exchange failed", ex);
        }
    }

    private ProfileResponse requestProfile(String accessToken) {
        try {
            return restClient.get()
                    .uri(USER_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(ProfileResponse.class);
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("GitHub", "Failed to read GitHub profile", ex);
        }
    }

    private String primaryVerifiedEmail(String accessToken) {
        try {
            EmailResponse[] emails = restClient.get()
                    .uri(EMAILS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(EmailResponse[].class);

            if (emails == null) {
                return null;
            }
            return List.of(emails).stream()
                    .filter(e -> Boolean.TRUE.equals(e.verified()) && e.email() != null)
                    .sorted(Comparator.comparing(e -> !Boolean.TRUE.equals(e.primary())))
                    .map(EmailResponse::email)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("GitHub", "Failed to read GitHub email addresses", ex);
        }
    }

    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("scope") String scope,
            @JsonProperty("error") String error,
            @JsonProperty("error_description") String errorDescription
    ) {
    }

    record ProfileResponse(
            Long id,
            String login,
            String name,
            String email,
            @JsonProperty("avatar_url") String avatarUrl
    ) {
    }

    record EmailResponse(
            String email,
            Boolean primary,
            Boolean verified,
            String visibility
    ) {
    }
}
