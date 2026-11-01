package com.docsync.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal repository metadata returned by the GitHub App API.
 */
public record GitHubRepositorySummary(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("clone_url") String cloneUrl,
        @JsonProperty("default_branch") String defaultBranch,
        @JsonProperty("private") boolean privateRepository,
        @JsonProperty("html_url") String htmlUrl
) {
}
