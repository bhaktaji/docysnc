package com.docsync.repository.service;

import com.docsync.common.exception.DuplicateResourceException;
import com.docsync.common.exception.ExternalServiceException;
import com.docsync.common.exception.ResourceNotFoundException;
import com.docsync.common.exception.ValidationException;
import com.docsync.connection.dto.RepositoryConnectionDto;
import com.docsync.connection.service.RepositoryConnectionService;
import com.docsync.github.GitHubAppClient;
import com.docsync.github.InstallationTokenService;
import com.docsync.github.dto.GitHubRepositorySummary;
import com.docsync.organization.entity.MemberRole;
import com.docsync.organization.service.OrganizationAuthorizationService;
import com.docsync.repository.clone.GitOperationException;
import com.docsync.repository.clone.GitRepositoryCloner;
import com.docsync.repository.dto.ConnectRepositoryRequest;
import com.docsync.repository.dto.RepositoryDto;
import com.docsync.repository.dto.UpdateRepositoryConfigurationRequest;
import com.docsync.repository.entity.Repository;
import com.docsync.repository.entity.RepositoryConfiguration;
import com.docsync.repository.entity.RepositoryStatus;
import com.docsync.repository.repository.RepositoryJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Repository onboarding: connect, configure, synchronize, and disconnect.
 *
 * <p>Repositories are always resolved through the owning organization, so a repository ID
 * from another tenant yields {@code 404}. Metadata is read through the installation token;
 * repository content is fetched into a confined workspace by {@link GitRepositoryCloner}.</p>
 */
@Service
@Transactional
public class RepositoryServiceImpl implements RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryServiceImpl.class);

    private final RepositoryJpaRepository repositoryRepository;
    private final OrganizationAuthorizationService authorizationService;
    private final RepositoryConnectionService connectionService;
    private final GitHubAppClient gitHubAppClient;
    private final InstallationTokenService tokenService;
    private final GitRepositoryCloner cloner;
    private final Clock clock;

    public RepositoryServiceImpl(RepositoryJpaRepository repositoryRepository,
                                 OrganizationAuthorizationService authorizationService,
                                 RepositoryConnectionService connectionService,
                                 GitHubAppClient gitHubAppClient,
                                 InstallationTokenService tokenService,
                                 GitRepositoryCloner cloner,
                                 Clock clock) {
        this.repositoryRepository = repositoryRepository;
        this.authorizationService = authorizationService;
        this.connectionService = connectionService;
        this.gitHubAppClient = gitHubAppClient;
        this.tokenService = tokenService;
        this.cloner = cloner;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepositoryDto> listRepositories(UUID actorId, UUID organizationId, RepositoryStatus status) {
        authorizationService.requireMembership(actorId, organizationId);

        List<Repository> repositories = status == null
                ? repositoryRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId)
                : repositoryRepository.findAllByOrganizationIdAndStatusOrderByCreatedAtDesc(
                        organizationId, status);

        Comparator<Repository> mostRecentlySynced = Comparator
                .comparing(Repository::getLastSyncedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Repository::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        return repositories.stream()
                .sorted(mostRecentlySynced)
                .map(RepositoryDto::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RepositoryDto getRepository(UUID actorId, UUID organizationId, UUID repositoryId) {
        authorizationService.requireMembership(actorId, organizationId);
        return RepositoryDto.from(requireRepository(organizationId, repositoryId));
    }

    @Override
    public RepositoryDto connectRepository(UUID actorId, UUID organizationId,
                                           ConnectRepositoryRequest request) {
        authorizationService.requireRole(actorId, organizationId, MemberRole.ADMIN);

        RepositoryConnectionDto connection =
                connectionService.getActiveConnection(organizationId, request.installationId());

        String token = tokenService.getToken(request.installationId());
        GitHubRepositorySummary remote =
                gitHubAppClient.getRepository(token, request.fullName().trim());

        Repository repository = repositoryRepository
                .findByOrganizationIdAndExternalId(organizationId, remote.id())
                .orElse(null);

        if (repository != null && repository.getStatus() != RepositoryStatus.ARCHIVED) {
            throw new DuplicateResourceException("Repository", remote.fullName());
        }

        String defaultBranch = request.defaultBranch() == null || request.defaultBranch().isBlank()
                ? (remote.defaultBranch() == null ? "main" : remote.defaultBranch())
                : request.defaultBranch().trim();
        String cloneUrl = remote.cloneUrl() == null || remote.cloneUrl().isBlank()
                ? "https://github.com/" + remote.fullName() + ".git"
                : remote.cloneUrl();

        if (repository == null) {
            repository = new Repository(organizationId, connection.id(), request.installationId(),
                    remote.id(), remote.name(), remote.fullName(), cloneUrl, defaultBranch);
        } else {
            repository.relink(connection.id(), request.installationId());
            repository.setName(remote.name());
            repository.setFullName(remote.fullName());
            repository.setCloneUrl(cloneUrl);
            repository.setDefaultBranch(defaultBranch);
            repository.markPending();
        }

        repository.setConfiguration(
                mergeConfiguration(RepositoryConfiguration.defaults(), request.configuration()));
        repositoryRepository.save(repository);

        log.info("Connected repository {} to organization {}", remote.fullName(), organizationId);
        return RepositoryDto.from(repository);
    }

    @Override
    public RepositoryDto updateConfiguration(UUID actorId, UUID organizationId, UUID repositoryId,
                                             UpdateRepositoryConfigurationRequest request) {
        authorizationService.requireRole(actorId, organizationId, MemberRole.MEMBER);
        Repository repository = requireRepository(organizationId, repositoryId);
        requireNotArchived(repository);

        repository.setConfiguration(
                mergeConfiguration(repository.getConfiguration(), request));
        repositoryRepository.save(repository);
        return RepositoryDto.from(repository);
    }

    @Override
    public RepositoryDto syncRepository(UUID actorId, UUID organizationId, UUID repositoryId) {
        authorizationService.requireRole(actorId, organizationId, MemberRole.MEMBER);
        Repository repository = requireRepository(organizationId, repositoryId);
        requireNotArchived(repository);

        repository.markCloning();

        try {
            String token = tokenService.getToken(repository.getInstallationId());
            GitHubRepositorySummary remote =
                    gitHubAppClient.getRepository(token, repository.getFullName());

            // FR-005: keep metadata fresh on every sync.
            repository.setName(remote.name());
            repository.setFullName(remote.fullName());
            if (remote.defaultBranch() != null && !remote.defaultBranch().isBlank()) {
                repository.setDefaultBranch(remote.defaultBranch());
            }

            GitRepositoryCloner.CloneResult result = cloner.checkout(
                    repository.getId(), repository.getFullName(),
                    repository.getDefaultBranch(), token);
            repository.markReady(result.commitSha(), Instant.now(clock));

            log.info("Synchronized repository {} at {}", repository.getFullName(), result.commitSha());
        } catch (GitOperationException ex) {
            repository.markError(truncate(ex.getMessage()));
            log.warn("Repository {} clone failed: {}", repository.getFullName(), ex.getMessage());
        } catch (ExternalServiceException ex) {
            repository.markError("GitHub repository metadata could not be read");
            log.warn("Repository {} metadata sync failed: {}", repository.getFullName(), ex.getMessage());
        }

        repositoryRepository.save(repository);
        return RepositoryDto.from(repository);
    }

    @Override
    public void disconnectRepository(UUID actorId, UUID organizationId, UUID repositoryId) {
        authorizationService.requireRole(actorId, organizationId, MemberRole.ADMIN);
        Repository repository = requireRepository(organizationId, repositoryId);
        repository.markArchived();
        repositoryRepository.save(repository);
        cloner.deleteWorkspace(repository.getId());
        log.info("Archived repository {} in organization {}", repositoryId, organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Repository> findRepositoryByInstallationAndExternalId(long installationId, long externalId) {
        return repositoryRepository.findByInstallationIdAndExternalId(installationId, externalId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Repository> findRepositoryById(UUID repositoryId) {
        return repositoryRepository.findById(repositoryId);
    }

    @Override
    @Transactional
    public void save(Repository repository) {
        repositoryRepository.save(repository);
    }

    private Repository requireRepository(UUID organizationId, UUID repositoryId) {
        return repositoryRepository.findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", repositoryId.toString()));
    }

    private void requireNotArchived(Repository repository) {
        if (repository.getStatus() == RepositoryStatus.ARCHIVED) {
            throw new ValidationException("status", "archived repositories cannot be modified");
        }
    }

    private RepositoryConfiguration mergeConfiguration(RepositoryConfiguration base,
                                                       UpdateRepositoryConfigurationRequest update) {
        if (update == null) {
            return base == null ? RepositoryConfiguration.defaults() : base;
        }
        RepositoryConfiguration current = base == null ? RepositoryConfiguration.defaults() : base;

        boolean autoAnalyze = update.autoAnalyze() == null ? current.autoAnalyze() : update.autoAnalyze();
        boolean analyzePullRequests = update.analyzePullRequests() == null
                ? current.analyzePullRequests()
                : update.analyzePullRequests();
        List<String> languages = update.languages() == null
                ? current.languages()
                : normalizeList(update.languages());
        List<String> excludePaths = update.excludePaths() == null
                ? current.excludePaths()
                : normalizeExcludePaths(update.excludePaths());

        return new RepositoryConfiguration(autoAnalyze, analyzePullRequests, languages, excludePaths);
    }

    private List<String> normalizeList(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private List<String> normalizeExcludePaths(List<String> values) {
        List<String> normalized = normalizeList(values).stream()
                .map(value -> value.replace('\\', '/'))
                .toList();
        for (String value : normalized) {
            if (value.startsWith("/") || value.contains("..") || value.matches("^[A-Za-z]:.*")) {
                throw new ValidationException("excludePaths",
                        "must be repository-relative glob patterns");
            }
        }
        return normalized;
    }

    private String truncate(String message) {
        String value = message == null || message.isBlank() ? "Repository synchronization failed" : message;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
