package com.docsync.identity.security;

import com.docsync.identity.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Issues short-lived, HMAC-signed JWT access tokens.
 */
@Service
public class JwtService {

    private static final String ISSUER = "docsync";

    private final JwtEncoder encoder;
    private final Duration accessTokenTtl;

    public JwtService(JwtEncoder encoder,
                      @Value("${docsync.security.access-token-ttl-minutes:15}") long accessTokenTtlMinutes) {
        if (accessTokenTtlMinutes <= 0) {
            throw new IllegalArgumentException("Access token TTL must be positive");
        }
        this.encoder = encoder;
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, accessTokenTtl.toSeconds());
    }
}
