package com.docsync.repository.repository;

import com.docsync.repository.entity.Repository;
import com.docsync.repository.entity.RepositoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryJpaRepository extends JpaRepository<Repository, UUID> {

    List<Repository> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<Repository> findAllByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, RepositoryStatus status);

    Optional<Repository> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Repository> findByOrganizationIdAndExternalId(UUID organizationId, Long externalId);

    Optional<Repository> findByInstallationIdAndExternalId(Long installationId, Long externalId);

    List<Repository> findAllByConnectionId(UUID connectionId);

    List<Repository> findAllByConnectionIdAndExternalIdIn(UUID connectionId, List<Long> externalIds);
}
