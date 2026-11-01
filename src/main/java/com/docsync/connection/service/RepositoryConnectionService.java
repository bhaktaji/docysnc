package com.docsync.connection.service;

import com.docsync.connection.dto.RepositoryConnectionDto;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * GitHub App installation connections. A connection links one installation to at most one
 * organization; unlinked installations stay {@code PENDING} until claimed by their installer.
 */
public interface RepositoryConnectionService {

    List<RepositoryConnectionDto> listConnections(UUID actorId, UUID organizationId);

    /** PENDING installations created by the authenticated user's GitHub account. */
    List<RepositoryConnectionDto> listClaimableConnections(UUID actorId);

    RepositoryConnectionDto claimConnection(UUID actorId, UUID organizationId, UUID connectionId);

    RepositoryConnectionDto revokeConnection(UUID actorId, UUID organizationId, UUID connectionId);

    /** Resolve an active, organization-owned installation or fail validation. */
    RepositoryConnectionDto getActiveConnection(UUID organizationId, long installationId);

    void handleInstallationEvent(String action, JsonNode payload);

    void handleInstallationRepositoriesEvent(String action, JsonNode payload);
}
