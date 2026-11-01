package com.docsync.identity.security;

public record IssuedToken(String token, long expiresInSeconds) {
}
