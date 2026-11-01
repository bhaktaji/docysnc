package com.docsync.analysis.entity;

import com.docsync.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "documentation_code_references")
public class DocumentationCodeReference extends BaseEntity {

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "page_id", nullable = false)
    private UUID pageId;

    @Column(name = "code_entity_id")
    private UUID codeEntityId;

    @Column(name = "qualified_name", nullable = false, length = 1000)
    private String qualifiedName;

    protected DocumentationCodeReference() {
    }

    public DocumentationCodeReference(UUID repositoryId, UUID pageId, UUID codeEntityId, String qualifiedName) {
        this.repositoryId = repositoryId;
        this.pageId = pageId;
        this.codeEntityId = codeEntityId;
        this.qualifiedName = qualifiedName;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getPageId() {
        return pageId;
    }

    public UUID getCodeEntityId() {
        return codeEntityId;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }
}
