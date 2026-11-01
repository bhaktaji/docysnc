package com.docsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DocSync Application - Developer documentation automation platform.
 *
 * <p>Keeps technical documentation synchronized with the actual state
 * of a software repository by detecting code changes, analyzing impact
 * on documentation, validating examples, and creating reviewable pull requests.</p>
 */
@SpringBootApplication
@EnableScheduling
public class DocSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocSyncApplication.class, args);
    }
}
