package com.docsync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the Spring application context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class DocSyncApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the application context starts without errors
    }
}
