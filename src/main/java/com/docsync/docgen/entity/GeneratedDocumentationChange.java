package com.docsync.docgen.entity;

import com.docsync.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "generated_documentation_changes")
public class GeneratedDocumentationChange {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @jakarta.persistence.Column(name = "id", updatable = false, nullable = false)
    private java.util.UUID id;

    @jakarta.persistence.Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @jakarta.persistence.Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    @Column(name = "generation_run_id", nullable = false)
    private UUID generationRunId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "analysis_id")
    private UUID analysisId;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType = ChangeType.UPDATE;

    @Column(name = "original_content", columnDefinition = "text")
    private String originalContent;

    @Column(name = "generated_content", nullable = false, columnDefinition = "text")
    private String generatedContent;

    @Column(name = "diff_content", columnDefinition = "text")
    private String diffContent;

    @Column(name = "provenance", columnDefinition = "text")
    private String provenance;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChangeStatus status = ChangeStatus.PENDING;

    @Column(name = "version", nullable = false)
    private int version = 1;

    protected GeneratedDocumentationChange() {
        this.createdAt = java.time.Instant.now();
        this.updatedAt = this.createdAt;
    }

    public GeneratedDocumentationChange(UUID generationRunId, UUID repositoryId, UUID analysisId, String filePath,
                                       ChangeType changeType, String originalContent, String generatedContent,
                                       String diffContent, String provenance, BigDecimal confidence, int version) {
        this.generationRunId = generationRunId;
        this.repositoryId = repositoryId;
        this.analysisId = analysisId;
        this.filePath = filePath;
        this.changeType = changeType;
        this.originalContent = originalContent;
        this.generatedContent = generatedContent;
        this.diffContent = diffContent;
        this.provenance = provenance;
        this.confidence = confidence;
        this.version = version;
        this.status = ChangeStatus.PENDING;
        this.createdAt = java.time.Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getGenerationRunId() {
        return generationRunId;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public String getFilePath() {
        return filePath;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getOriginalContent() {
        return originalContent;
    }

    public String getGeneratedContent() {
        return generatedContent;
    }

    public void setGeneratedContent(String generatedContent) {
        this.generatedContent = generatedContent;
    }

    public String getDiffContent() {
        return diffContent;
    }

    public void setDiffContent(String diffContent) {
        this.diffContent = diffContent;
    }

    public String getProvenance() {
        return provenance;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public ChangeStatus getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public java.util.UUID getId() {
        return id;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    public java.time.Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(ChangeStatus status) {
        this.status = status;
        this.updatedAt = java.time.Instant.now();
    }
}
