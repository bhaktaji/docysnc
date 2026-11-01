package com.docsync.docgen.entity;

import com.docsync.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generation_runs")
public class GenerationRun extends BaseEntity {

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "analysis_id")
    private UUID analysisId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "RUNNING";

    @Column(name = "completed_at")
    private Instant completedAt;

    protected GenerationRun() {
    }

    public GenerationRun(UUID repositoryId, UUID analysisId) {
        this.repositoryId = repositoryId;
        this.analysisId = analysisId;
        this.status = "RUNNING";
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
