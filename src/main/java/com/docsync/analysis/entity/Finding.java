package com.docsync.analysis.entity;

import com.docsync.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "findings")
public class Finding extends BaseEntity {

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private FindingSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private FindingCategory category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FindingStatus status = FindingStatus.OPEN;

    protected Finding() {
    }

    public Finding(UUID repositoryId, UUID analysisId, FindingSeverity severity, FindingCategory category,
                   String title, String description, UUID entityId, String filePath, Integer lineNumber) {
        this.repositoryId = repositoryId;
        this.analysisId = analysisId;
        this.severity = severity;
        this.category = category;
        this.title = title;
        this.description = description;
        this.entityId = entityId;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.status = FindingStatus.OPEN;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public FindingSeverity getSeverity() {
        return severity;
    }

    public FindingCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public FindingStatus getStatus() {
        return status;
    }
}
