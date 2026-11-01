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

import java.util.UUID;

@Entity
@Table(name = "documentation_pages")
public class DocumentationPage extends BaseEntity {

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private DocumentationType type;

    @Column(name = "path", nullable = false, length = 512)
    private String path;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "last_analyzed_commit", length = 40)
    private String lastAnalyzedCommit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;

    protected DocumentationPage() {
    }

    public DocumentationPage(UUID repositoryId, UUID analysisId, String title, DocumentationType type,
                             String path, String contentHash, String lastAnalyzedCommit) {
        this.repositoryId = repositoryId;
        this.analysisId = analysisId;
        this.title = title;
        this.type = type;
        this.path = path;
        this.contentHash = contentHash;
        this.lastAnalyzedCommit = lastAnalyzedCommit;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public String getTitle() {
        return title;
    }

    public DocumentationType getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getLastAnalyzedCommit() {
        return lastAnalyzedCommit;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }
}
