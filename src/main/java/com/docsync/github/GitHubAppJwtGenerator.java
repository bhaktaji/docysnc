package com.docsync.github;

import com.docsync.common.exception.ExternalServiceException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Generates short-lived RS256 JWTs that authenticate DocSync as the GitHub App.
 *
 * <p>The JWT is used only to request installation access tokens. Per GitHub's rules the
 * expiry is at most 10 minutes; this implementation backdates {@code iat} by 60 seconds
 * for clock skew and expires 9 minutes after issuance.</p>
 */
@Component
public class GitHubAppJwtGenerator {

    static final Duration BACKDATE = Duration.ofSeconds(60);
    static final Duration LIFETIME = Duration.ofMinutes(9);

    private final Long appId;
    private final String privateKeyPem;
    private final Clock clock;
    private volatile PrivateKey privateKey;

    public GitHubAppJwtGenerator(@Value("${docsync.github.app-id:}") String appId,
                                 @Value("${docsync.github.private-key:}") String privateKeyPem,
                                 Clock clock) {
        this.appId = parseAppId(appId);
        this.privateKeyPem = privateKeyPem;
        this.clock = clock;
    }

    public String generate() {
        if (appId == null) {
            throw new ExternalServiceException("GitHub", "GitHub App ID is not configured");
        }

        Instant now = clock.instant();
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    new JWTClaimsSet.Builder()
                            .issuer(String.valueOf(appId))
                            .issueTime(Date.from(now.minus(BACKDATE)))
                            .expirationTime(Date.from(now.plus(LIFETIME)))
                            .build());
            jwt.sign(new RSASSASigner(privateKey()));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new ExternalServiceException("GitHub", "Failed to sign GitHub App JWT", ex);
        }
    }

    private PrivateKey privateKey() {
        PrivateKey loaded = privateKey;
        if (loaded == null) {
            synchronized (this) {
                loaded = privateKey;
                if (loaded == null) {
                    loaded = PemPrivateKeyLoader.load(privateKeyPem);
                    privateKey = loaded;
                }
            }
        }
        return loaded;
    }

    private static Long parseAppId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("docsync.github.app-id must be numeric");
        }
    }
}
