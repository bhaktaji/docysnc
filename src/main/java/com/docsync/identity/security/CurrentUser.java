package com.docsync.identity.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the authenticated user's ID from the validated JWT.
 */
@Component
public class CurrentUser {

    public UUID id(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return UUID.fromString(jwtAuthentication.getToken().getSubject());
        }
        throw new IllegalStateException("No JWT authentication present");
    }

    public UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
