package com.docsync.connection.repository;

import com.docsync.connection.entity.ConnectionStatus;
import com.docsync.connection.entity.GitProvider;
import com.docsync.connection.entity.RepositoryConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryConnectionRepository extends JpaRepository<RepositoryConnection, UUID> {

    Optional<RepositoryConnection> findByProviderAndInstallationId(GitProvider provider, Long installationId);

    List<RepositoryConnection> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<RepositoryConnection> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<RepositoryConnection> findAllByInstallerGithubIdAndOrganizationIdIsNullAndStatusOrderByCreatedAtDesc(
            Long installerGithubId, ConnectionStatus status);
}
