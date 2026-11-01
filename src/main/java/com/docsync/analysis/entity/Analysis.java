package com.docsync.analysis.entity;

import com.docsync.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analyses")
public class Analysis extends BaseEntity {

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "branch", nullable = false, length = 255)
    private String branch;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private AnalysisScope scope = AnalysisScope.FULL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status = AnalysisStatus.QUEUED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats", columnDefinition = "jsonb")
    private JsonNode stats;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Analysis() {
    }

    public Analysis(UUID repositoryId, UUID jobId, String branch, String commitSha, AnalysisScope scope) {
        this.repositoryId = repositoryId;
        this.jobId = jobId;
        this.branch = branch;
        this.commitSha = commitSha;
        this.scope = scope;
        this.status = AnalysisStatus.QUEUED;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public AnalysisScope getScope() {
        return scope;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public JsonNode getStats() {
        return stats;
    }

    public void setStats(JsonNode stats) {
        this.stats = stats;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
