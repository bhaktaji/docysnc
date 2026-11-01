package com.docsync.github;

import com.docsync.github.dto.InstallationToken;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstallationTokenServiceTest {

    private static final Instant START = Instant.parse("2026-01-15T12:00:00Z");

    @Test
    void cachesTokensUntilFiveMinutesRemainThenRefreshes() {
        MutableClock clock = new MutableClock(START);
        GitHubAppClient client = mock(GitHubAppClient.class);
        GitHubAppJwtGenerator jwtGenerator = mock(GitHubAppJwtGenerator.class);
        when(jwtGenerator.generate()).thenReturn("app-jwt");
        when(client.createInstallationToken("app-jwt", 7L))
                .thenReturn(new InstallationToken("first", START.plus(Duration.ofHours(1))))
                .thenReturn(new InstallationToken("second", START.plus(Duration.ofHours(2))));

        InstallationTokenService service = new InstallationTokenService(client, jwtGenerator, clock);

        assertEquals("first", service.getToken(7L));
        clock.advance(Duration.ofMinutes(50));
        assertEquals("first", service.getToken(7L));
        verify(client, times(1)).createInstallationToken(anyStringValue(), eq(7L));

        // 55 minutes elapsed -> 5 minutes remain, so the token is refreshed proactively.
        clock.advance(Duration.ofMinutes(5));
        assertEquals("second", service.getToken(7L));
        verify(client, times(2)).createInstallationToken(anyStringValue(), eq(7L));
    }

    @Test
    void invalidationForcesNewToken() {
        MutableClock clock = new MutableClock(START);
        GitHubAppClient client = mock(GitHubAppClient.class);
        GitHubAppJwtGenerator jwtGenerator = mock(GitHubAppJwtGenerator.class);
        when(jwtGenerator.generate()).thenReturn("app-jwt");
        when(client.createInstallationToken("app-jwt", 9L))
                .thenReturn(new InstallationToken("one", START.plus(Duration.ofHours(1))))
                .thenReturn(new InstallationToken("two", START.plus(Duration.ofHours(1))));

        InstallationTokenService service = new InstallationTokenService(client, jwtGenerator, clock);
        assertEquals("one", service.getToken(9L));
        assertTrue(service.isCached(9L));

        service.invalidate(9L);
        assertFalse(service.isCached(9L));
        assertEquals("two", service.getToken(9L));
    }

    @Test
    void concurrentCallersShareOneTokenRequest() throws Exception {
        MutableClock clock = new MutableClock(START);
        GitHubAppClient client = mock(GitHubAppClient.class);
        GitHubAppJwtGenerator jwtGenerator = mock(GitHubAppJwtGenerator.class);
        when(jwtGenerator.generate()).thenReturn("app-jwt");
        when(client.createInstallationToken("app-jwt", 11L)).thenAnswer(invocation -> {
            Thread.sleep(50);
            return new InstallationToken("shared", START.plus(Duration.ofHours(1)));
        });

        InstallationTokenService service = new InstallationTokenService(client, jwtGenerator, clock);
        int callers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return service.getToken(11L);
                }));
            }
            start.countDown();
            for (var future : futures) {
                assertEquals("shared", future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        verify(client, times(1)).createInstallationToken(anyStringValue(), eq(11L));
        verify(jwtGenerator, times(1)).generate();
    }

    private static String anyStringValue() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
