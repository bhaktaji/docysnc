package com.docsync.github;

import com.docsync.github.dto.InstallationToken;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and caches GitHub App installation access tokens in memory only.
 *
 * <p>Tokens are refreshed proactively when less than five minutes remain before expiry
 * (FR-009). Cached tokens are never written to the database and are discarded on process
 * restart or explicit invalidation.</p>
 */
@Service
public class InstallationTokenService {

    static final Duration REFRESH_BUFFER = Duration.ofMinutes(5);

    private final GitHubAppClient client;
    private final GitHubAppJwtGenerator jwtGenerator;
    private final Clock clock;
    private final Map<Long, CachedToken> cache = new ConcurrentHashMap<>();

    public InstallationTokenService(GitHubAppClient client,
                                    GitHubAppJwtGenerator jwtGenerator,
                                    Clock clock) {
        this.client = client;
        this.jwtGenerator = jwtGenerator;
        this.clock = clock;
    }

    /**
     * Returns a usable token for the installation, reusing the cached value or requesting
     * a new one. Concurrent callers share a single in-flight request per installation.
     */
    public String getToken(long installationId) {
        return cache.compute(installationId, (id, cached) -> {
            Instant now = clock.instant();
            if (cached != null && cached.expiresAt().isAfter(now.plus(REFRESH_BUFFER))) {
                return cached;
            }
            InstallationToken issued = client.createInstallationToken(jwtGenerator.generate(), id);
            return new CachedToken(issued.token(), issued.expiresAt());
        }).token();
    }

    /**
     * Drops a cached token, for example after an installation is suspended or removed.
     */
    public void invalidate(long installationId) {
        cache.remove(installationId);
    }

    /** Visible for tests. */
    boolean isCached(long installationId) {
        return cache.containsKey(installationId);
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
