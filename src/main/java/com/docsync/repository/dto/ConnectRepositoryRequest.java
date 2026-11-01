package com.docsync.repository.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Connect an existing GitHub App installation repository to an organization.
 */
public record ConnectRepositoryRequest(
        @NotNull Long installationId,
        @NotBlank
        @Size(max = 512)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$",
                message = "must be in owner/repository form")
        String fullName,
        @Size(max = 100) String defaultBranch,
        @Valid UpdateRepositoryConfigurationRequest configuration
) {
}
