package com.docsync.repository.dto;

import com.docsync.repository.entity.Repository;
import com.docsync.repository.entity.RepositoryConfiguration;
import com.docsync.repository.entity.RepositoryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository view for tenant APIs. Clone credentials and workspace paths are never exposed.
 */
public record RepositoryDto(
        UUID id,
        UUID organizationId,
        UUID connectionId,
        String provider,
        Long installationId,
        Long externalId,
        String name,
        String fullName,
        String cloneUrl,
        String defaultBranch,
        RepositoryStatus status,
        RepositoryConfiguration configuration,
        String errorMessage,
        Instant lastSyncedAt,
        String lastCommitSha,
        Instant lastAnalysisAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static RepositoryDto from(Repository repository) {
        return new RepositoryDto(
                repository.getId(),
                repository.getOrganizationId(),
                repository.getConnectionId(),
                repository.getProvider().name(),
                repository.getInstallationId(),
                repository.getExternalId(),
                repository.getName(),
                repository.getFullName(),
                repository.getCloneUrl(),
                repository.getDefaultBranch(),
                repository.getStatus(),
                repository.getConfiguration(),
                repository.getErrorMessage(),
                repository.getLastSyncedAt(),
                repository.getLastCommitSha(),
                repository.getLastAnalysisAt(),
                repository.getCreatedAt(),
                repository.getUpdatedAt());
    }
}
