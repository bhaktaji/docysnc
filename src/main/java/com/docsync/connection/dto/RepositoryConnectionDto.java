package com.docsync.connection.dto;

import com.docsync.connection.entity.RepositoryConnection;

import java.time.Instant;
import java.util.UUID;

/**
 * GitHub App installation visible to a tenant. Installation tokens are never exposed.
 */
public record RepositoryConnectionDto(
        UUID id,
        UUID organizationId,
        String provider,
        Long installationId,
        String accountLogin,
        String accountType,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static RepositoryConnectionDto from(RepositoryConnection connection) {
        return new RepositoryConnectionDto(
                connection.getId(),
                connection.getOrganizationId(),
                connection.getProvider().name(),
                connection.getInstallationId(),
                connection.getAccountLogin(),
                connection.getAccountType(),
                connection.getStatus().name(),
                connection.getCreatedAt(),
                connection.getUpdatedAt());
    }
}
