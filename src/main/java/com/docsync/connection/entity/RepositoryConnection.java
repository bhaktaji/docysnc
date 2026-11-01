package com.docsync.connection.entity;

import com.docsync.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A GitHub App installation. The optional organization link is established by an
 * installation webhook (when the installer is a known admin) or by an explicit claim.
 * Installation tokens are never persisted; they live only in the in-memory cache.
 */
@Entity
@Table(name = "repository_connections")
public class RepositoryConnection extends BaseEntity {

    @Column(name = "organization_id")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private GitProvider provider = GitProvider.GITHUB;

    @Column(name = "installation_id", nullable = false)
    private Long installationId;

    @Column(name = "account_login", length = 255)
    private String accountLogin;

    @Column(name = "account_type", length = 50)
    private String accountType;

    @Column(name = "installer_github_id")
    private Long installerGithubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionStatus status = ConnectionStatus.PENDING;

    protected RepositoryConnection() {
    }

    public RepositoryConnection(GitProvider provider, Long installationId) {
        this.provider = provider;
        this.installationId = installationId;
        this.status = ConnectionStatus.PENDING;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public GitProvider getProvider() {
        return provider;
    }

    public void setProvider(GitProvider provider) {
        this.provider = provider;
    }

    public Long getInstallationId() {
        return installationId;
    }

    public void setInstallationId(Long installationId) {
        this.installationId = installationId;
    }

    public String getAccountLogin() {
        return accountLogin;
    }

    public void setAccountLogin(String accountLogin) {
        this.accountLogin = accountLogin;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public Long getInstallerGithubId() {
        return installerGithubId;
    }

    public void setInstallerGithubId(Long installerGithubId) {
        this.installerGithubId = installerGithubId;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == ConnectionStatus.ACTIVE && organizationId != null;
    }
}
