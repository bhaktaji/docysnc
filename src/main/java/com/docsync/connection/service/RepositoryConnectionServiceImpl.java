package com.docsync.connection.service;

import com.docsync.common.exception.AccessDeniedException;
import com.docsync.common.exception.ResourceNotFoundException;
import com.docsync.common.exception.ValidationException;
import com.docsync.connection.dto.RepositoryConnectionDto;
import com.docsync.connection.entity.ConnectionStatus;
import com.docsync.connection.entity.GitProvider;
import com.docsync.connection.entity.RepositoryConnection;
import com.docsync.connection.repository.RepositoryConnectionRepository;
import com.docsync.github.GitHubAppClient;
import com.docsync.github.InstallationTokenService;
import com.docsync.github.dto.GitHubRepositorySummary;
import com.docsync.identity.dto.UserDto;
import com.docsync.identity.service.IdentityService;
import com.docsync.organization.entity.MemberRole;
import com.docsync.organization.service.OrganizationAuthorizationService;
import com.docsync.repository.entity.Repository;
import com.docsync.repository.repository.RepositoryJpaRepository;
import com.docsync.webhook.exception.InvalidWebhookException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Installation lifecycle and organization linkage.
 *
 * <p>Installation tokens are only obtained on demand and never stored. Webhook processing is
 * idempotent: an installation is keyed by {@code (provider, installationId)} and repositories
 * by {@code (organizationId, externalId)}.</p>
 */
@Service
@Transactional
public class RepositoryConnectionServiceImpl implements RepositoryConnectionService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryConnectionServiceImpl.class);

    private final RepositoryConnectionRepository connectionRepository;
    private final RepositoryJpaRepository repositoryRepository;
    private final OrganizationAuthorizationService authorizationService;
    private final IdentityService identityService;
    private final GitHubAppClient gitHubAppClient;
    private final InstallationTokenService tokenService;

    public RepositoryConnectionServiceImpl(RepositoryConnectionRepository connectionRepository,
                                           RepositoryJpaRepository repositoryRepository,
                                           OrganizationAuthorizationService authorizationService,
                                           IdentityService identityService,
                                           GitHubAppClient gitHubAppClient,
                                           InstallationTokenService tokenService) {
        this.connectionRepository = connectionRepository;
        this.repositoryRepository = repositoryRepository;
        this.authorizationService = authorizationService;
        this.identityService = identityService;
        this.gitHubAppClient = gitHubAppClient;
        this.tokenService = tokenService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepositoryConnectionDto> listConnections(UUID actorId, UUID organizationId) {
        authorizationService.requireMembership(actorId, organizationId);
        return connectionRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(RepositoryConnectionDto::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepositoryConnectionDto> listClaimableConnections(UUID actorId) {
        UserDto user = identityService.getUser(actorId);
        if (user.githubId() == null) {
            return List.of();
        }
        return connectionRepository
                .findAllByInstallerGithubIdAndOrganizationIdIsNullAndStatusOrderByCreatedAtDesc(
                        user.githubId(), ConnectionStatus.PENDING)
                .stream()
                .map(RepositoryConnectionDto::from)
                .toList();
    }

    @Override
    public RepositoryConnectionDto claimConnection(UUID actorId, UUID organizationId, UUID connectionId) {
        authorizationService.requireRole(actorId, organizationId, MemberRole.ADMIN);

        RepositoryConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection", connectionId.toString()));

        if (connection.getOrganizationId() != null
                && !connection.getOrganizationId().equals(organizationId)) {
            // Do not disclose that another tenant owns this installation.
            throw new ResourceNotFoundException("Connection", connectionId.toString());
        }
        if (connection.getStatus() != ConnectionStatus.PENDING) {
            throw new ValidationException("status", "only pending connections can be claimed");
        }

        UserDto actor = identityService.getUser(actorId);
        if (actor.githubId() == null || !actor.githubId().equals(connection.getInstallerGithubId())) {
            throw new AccessDeniedException("Only the GitHub account that installed the App can claim it");
        }

        connection.setOrganizationId(organizationId);
        connection.setStatus(ConnectionStatus.ACTIVE);
        connectionRepository.save(connection);

        importInstallationRepositories(connection);
        log.info("Claimed GitHub installation {} for organization {}", connection.getInstallationId(),
                organizationId);
        return RepositoryConnectionDto.from(connection);
    }

    @Override
    public RepositoryConnectionDto revokeConnection(UUID actorId, UUID organizationId, UUID connectionId) {
        authorizationService.requireRole(actorId, organizationId, MemberRole.ADMIN);

        RepositoryConnection connection = connectionRepository.findByIdAndOrganizationId(connectionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Connection", connectionId.toString()));

        if (connection.getStatus() != ConnectionStatus.REVOKED) {
            connection.setStatus(ConnectionStatus.REVOKED);
            connectionRepository.save(connection);
            tokenService.invalidate(connection.getInstallationId());
            archiveRepositories(connection);
            log.info("Revoked GitHub installation {} for organization {}",
                    connection.getInstallationId(), organizationId);
        }
        return RepositoryConnectionDto.from(connection);
    }

    @Override
    @Transactional(readOnly = true)
    public RepositoryConnectionDto getActiveConnection(UUID organizationId, long installationId) {
        RepositoryConnection connection = connectionRepository
                .findByProviderAndInstallationId(GitProvider.GITHUB, installationId)
                .orElseThrow(() -> new ValidationException("installationId",
                        "no GitHub App installation is known for this ID"));

        if (!connection.isActive() || !organizationId.equals(connection.getOrganizationId())) {
            throw new ValidationException("installationId",
                    "the GitHub App installation is not active for this organization");
        }
        return RepositoryConnectionDto.from(connection);
    }

    @Override
    public void handleInstallationEvent(String action, JsonNode payload) {
        JsonNode installation = payload.path("installation");
        long installationId = installation.path("id").asLong(0);
        if (installationId <= 0) {
            throw new InvalidWebhookException("installation.id is missing");
        }

        RepositoryConnection connection = connectionRepository
                .findByProviderAndInstallationId(GitProvider.GITHUB, installationId)
                .orElseGet(() -> new RepositoryConnection(GitProvider.GITHUB, installationId));

        connection.setAccountLogin(text(installation.path("account"), "login"));
        connection.setAccountType(text(installation.path("account"), "type"));
        Long senderId = longOrNull(payload.path("sender"), "id");
        if (senderId != null) {
            connection.setInstallerGithubId(senderId);
        }

        switch (action == null ? "" : action) {
            case "created" -> {
                autoLink(connection, senderId);
                if (connection.getOrganizationId() != null) {
                    connection.setStatus(ConnectionStatus.ACTIVE);
                }
                connectionRepository.save(connection);
                upsertRepositories(connection, payload.path("repositories"));
                log.info("Processed installation.created for installation {}", installationId);
                return;
            }
            case "deleted", "removed" -> {
                if (connection.getId() == null) {
                    return;
                }
                connection.setStatus(ConnectionStatus.REVOKED);
                connectionRepository.save(connection);
                tokenService.invalidate(installationId);
                archiveRepositories(connection);
                log.info("Processed installation.deleted for installation {}", installationId);
                return;
            }
            case "suspend", "suspended" -> {
                connection.setStatus(ConnectionStatus.SUSPENDED);
                connectionRepository.save(connection);
                tokenService.invalidate(installationId);
                return;
            }
            case "unsuspend", "unsuspended" -> {
                connection.setStatus(connection.getOrganizationId() == null
                        ? ConnectionStatus.PENDING
                        : ConnectionStatus.ACTIVE);
                connectionRepository.save(connection);
                return;
            }
            default -> {
                // new_permissions_accepted and forward-compatible actions keep the link.
                if (connection.getOrganizationId() != null
                        && connection.getStatus() != ConnectionStatus.SUSPENDED
                        && connection.getStatus() != ConnectionStatus.REVOKED) {
                    connection.setStatus(ConnectionStatus.ACTIVE);
                }
                connectionRepository.save(connection);
            }
        }
    }

    @Override
    public void handleInstallationRepositoriesEvent(String action, JsonNode payload) {
        JsonNode installation = payload.path("installation");
        long installationId = installation.path("id").asLong(0);
        if (installationId <= 0) {
            throw new InvalidWebhookException("installation.id is missing");
        }

        RepositoryConnection connection = connectionRepository
                .findByProviderAndInstallationId(GitProvider.GITHUB, installationId)
                .orElse(null);
        if (connection == null) {
            log.warn("Received installation_repositories event for unknown installation {}", installationId);
            return;
        }

        if ("added".equals(action)) {
            upsertRepositories(connection, payload.path("repositories_added"));
        } else if ("removed".equals(action)) {
            archiveRemovedRepositories(connection, payload.path("repositories_removed"));
        }
    }

    /**
     * When the installer is a DocSync user who administers exactly one organization, the
     * installation is linked automatically. Otherwise it stays PENDING and claimable.
     */
    private void autoLink(RepositoryConnection connection, Long senderId) {
        if (connection.getOrganizationId() != null || senderId == null) {
            return;
        }
        identityService.findByGithubId(senderId).ifPresent(user -> {
            List<UUID> adminOrganizations = authorizationService.getAdminOrganizationIds(user.id());
            if (adminOrganizations.size() == 1) {
                connection.setOrganizationId(adminOrganizations.get(0));
            }
        });
    }

    private void importInstallationRepositories(RepositoryConnection connection) {
        String token = tokenService.getToken(connection.getInstallationId());
        List<GitHubRepositorySummary> repositories =
                gitHubAppClient.listInstallationRepositories(token);
        for (GitHubRepositorySummary summary : repositories) {
            upsertRepository(connection, summary.id(), summary.name(), summary.fullName(),
                    summary.cloneUrl(), summary.defaultBranch());
        }
    }

    private void upsertRepositories(RepositoryConnection connection, JsonNode repositories) {
        if (connection.getOrganizationId() == null || repositories == null || !repositories.isArray()) {
            return;
        }
        for (JsonNode node : repositories) {
            Long externalId = longOrNull(node, "id");
            String fullName = text(node, "full_name");
            if (externalId == null || fullName == null) {
                continue;
            }
            String name = text(node, "name");
            if (name == null) {
                name = fullName.substring(fullName.indexOf('/') + 1);
            }
            String cloneUrl = text(node, "clone_url");
            if (cloneUrl == null) {
                cloneUrl = "https://github.com/" + fullName + ".git";
            }
            upsertRepository(connection, externalId, name, fullName, cloneUrl,
                    text(node, "default_branch"));
        }
    }

    private void upsertRepository(RepositoryConnection connection, Long externalId, String name,
                                  String fullName, String cloneUrl, String defaultBranch) {
        UUID organizationId = connection.getOrganizationId();
        if (organizationId == null || externalId == null || fullName == null) {
            return;
        }
        String resolvedCloneUrl = cloneUrl == null || cloneUrl.isBlank()
                ? "https://github.com/" + fullName + ".git"
                : cloneUrl;

        Repository repository = repositoryRepository
                .findByOrganizationIdAndExternalId(organizationId, externalId)
                .orElseGet(() -> new Repository(organizationId, connection.getId(),
                        connection.getInstallationId(), externalId, name, fullName, resolvedCloneUrl,
                        defaultBranch));

        repository.setName(name);
        repository.setFullName(fullName);
        repository.setCloneUrl(resolvedCloneUrl);
        if (defaultBranch != null && !defaultBranch.isBlank()) {
            repository.setDefaultBranch(defaultBranch);
        }
        repository.relink(connection.getId(), connection.getInstallationId());
        if (repository.getStatus() == com.docsync.repository.entity.RepositoryStatus.ARCHIVED) {
            repository.markPending();
        }
        repositoryRepository.save(repository);
    }

    private void archiveRemovedRepositories(RepositoryConnection connection, JsonNode removed) {
        if (removed == null || !removed.isArray() || connection.getId() == null) {
            return;
        }
        List<Long> externalIds = new java.util.ArrayList<>();
        removed.forEach(node -> {
            Long id = longOrNull(node, "id");
            if (id != null) {
                externalIds.add(id);
            }
        });
        if (externalIds.isEmpty()) {
            return;
        }
        repositoryRepository.findAllByConnectionIdAndExternalIdIn(connection.getId(), externalIds)
                .forEach(Repository::markArchived);
    }

    private void archiveRepositories(RepositoryConnection connection) {
        if (connection.getId() == null) {
            return;
        }
        repositoryRepository.findAllByConnectionId(connection.getId())
                .forEach(Repository::markArchived);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.canConvertToLong() ? null : value.asLong();
    }
}
