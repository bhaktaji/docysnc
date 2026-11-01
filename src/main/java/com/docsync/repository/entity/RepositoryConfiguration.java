package com.docsync.repository.entity;

import java.util.List;

/**
 * Tenant-editable repository analysis settings, persisted as JSONB.
 */
public record RepositoryConfiguration(
        boolean autoAnalyze,
        boolean analyzePullRequests,
        List<String> languages,
        List<String> excludePaths
) {

    public RepositoryConfiguration {
        languages = languages == null ? List.of() : List.copyOf(languages);
        excludePaths = excludePaths == null ? List.of() : List.copyOf(excludePaths);
    }

    public static RepositoryConfiguration defaults() {
        return new RepositoryConfiguration(true, true, List.of(), List.of());
    }
}
