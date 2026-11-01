package com.docsync.repository.service;

import com.docsync.repository.dto.ConnectRepositoryRequest;
import com.docsync.repository.dto.RepositoryDto;
import com.docsync.repository.dto.UpdateRepositoryConfigurationRequest;
import com.docsync.repository.entity.RepositoryStatus;

import java.util.List;
import java.util.UUID;

/**
 * Repository onboarding within an organization. Every operation is tenant-scoped.
 */
public interface RepositoryService {

    List<RepositoryDto> listRepositories(UUID actorId, UUID organizationId, RepositoryStatus status);

    RepositoryDto getRepository(UUID actorId, UUID organizationId, UUID repositoryId);

    RepositoryDto connectRepository(UUID actorId, UUID organizationId, ConnectRepositoryRequest request);

    RepositoryDto updateConfiguration(UUID actorId, UUID organizationId, UUID repositoryId,
                                      UpdateRepositoryConfigurationRequest request);

    RepositoryDto syncRepository(UUID actorId, UUID organizationId, UUID repositoryId);

    void disconnectRepository(UUID actorId, UUID organizationId, UUID repositoryId);

    java.util.Optional<com.docsync.repository.entity.Repository> findRepositoryByInstallationAndExternalId(
            long installationId, long externalId);

    java.util.Optional<com.docsync.repository.entity.Repository> findRepositoryById(java.util.UUID repositoryId);

    void save(com.docsync.repository.entity.Repository repository);
}
