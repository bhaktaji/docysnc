package com.docsync.repository.entity;

import com.docsync.common.entity.BaseEntity;
import com.docsync.connection.entity.GitProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A repository onboarded into an organization. Repository content is never trusted:
 * paths are confined to a per-repository workspace before any file access.
 */
@Entity
@Table(name = "repositories")
public class Repository extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private GitProvider provider = GitProvider.GITHUB;

    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    @Column(name = "external_id", nullable = false)
    private Long externalId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "full_name", nullable = false, length = 512)
    private String fullName;

    /** Informational HTTPS clone URL. Git operations derive the URL from configuration. */
    @Column(name = "clone_url", length = 1024)
    private String cloneUrl;

    @Column(name = "default_branch", nullable = false, length = 100)
    private String defaultBranch = "main";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RepositoryStatus status = RepositoryStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false)
    private RepositoryConfiguration configuration = RepositoryConfiguration.defaults();

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_commit_sha", length = 40)
    private String lastCommitSha;

    @Column(name = "last_analysis_at")
    private Instant lastAnalysisAt;

    @Column(name = "last_processed_commit_sha", length = 40)
    private String lastProcessedCommitSha;

    @Column(name = "last_processed_at")
    private Instant lastProcessedAt;

    protected Repository() {
    }

    public Repository(UUID organizationId, UUID connectionId, Long installationId,
                      Long externalId, String name, String fullName, String cloneUrl,
                      String defaultBranch) {
        this.organizationId = organizationId;
        this.connectionId = connectionId;
        this.installationId = installationId;
        this.externalId = externalId;
        this.name = name;
        this.fullName = fullName;
        this.cloneUrl = cloneUrl;
        this.defaultBranch = defaultBranch == null || defaultBranch.isBlank() ? "main" : defaultBranch;
        this.status = RepositoryStatus.PENDING;
        this.configuration = RepositoryConfiguration.defaults();
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public GitProvider getProvider() {
        return provider;
    }

    public Long getInstallationId() {
        return installationId;
    }

    public Long getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public RepositoryStatus getStatus() {
        return status;
    }

    public void setStatus(RepositoryStatus status) {
        this.status = status;
    }

    public RepositoryConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(RepositoryConfiguration configuration) {
        this.configuration = configuration == null ? RepositoryConfiguration.defaults() : configuration;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getLastCommitSha() {
        return lastCommitSha;
    }

    public void setLastCommitSha(String lastCommitSha) {
        this.lastCommitSha = lastCommitSha;
    }

    public Instant getLastAnalysisAt() {
        return lastAnalysisAt;
    }

    public void setLastAnalysisAt(Instant lastAnalysisAt) {
        this.lastAnalysisAt = lastAnalysisAt;
    }

    public String getLastProcessedCommitSha() {
        return lastProcessedCommitSha;
    }

    public void setLastProcessedCommitSha(String lastProcessedCommitSha) {
        this.lastProcessedCommitSha = lastProcessedCommitSha;
    }

    public Instant getLastProcessedAt() {
        return lastProcessedAt;
    }

    public void setLastProcessedAt(Instant lastProcessedAt) {
        this.lastProcessedAt = lastProcessedAt;
    }

    /** Re-points a repository after it is moved between installations. */
    public void relink(UUID connectionId, Long installationId) {
        this.connectionId = connectionId;
        this.installationId = installationId;
    }

    public void markPending() {
        this.status = RepositoryStatus.PENDING;
        this.errorMessage = null;
    }

    public void markCloning() {
        this.status = RepositoryStatus.CLONING;
        this.errorMessage = null;
    }

    public void markReady(String commitSha, Instant syncedAt) {
        this.status = RepositoryStatus.READY;
        this.errorMessage = null;
        this.lastCommitSha = commitSha;
        this.lastSyncedAt = syncedAt;
    }

    public void markError(String message) {
        this.status = RepositoryStatus.ERROR;
        this.errorMessage = message;
    }

    public void markArchived() {
        this.status = RepositoryStatus.ARCHIVED;
    }
}
