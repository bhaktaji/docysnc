package com.docsync.repository.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partial repository configuration update. Omitted fields keep their current value.
 */
public record UpdateRepositoryConfigurationRequest(
        Boolean autoAnalyze,
        Boolean analyzePullRequests,
        @Size(max = 50) List<@Size(max = 50) String> languages,
        @Size(max = 100) List<@Size(max = 512) String> excludePaths
) {
}
