package com.docsync.analysis.entity;

import com.docsync.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "code_entities")
public class CodeEntity extends BaseEntity {

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private CodeEntityType entityType;

    @Column(name = "qualified_name", nullable = false, length = 1000)
    private String qualifiedName;

    @Column(name = "simple_name", nullable = false, length = 255)
    private String simpleName;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "modifiers", length = 100)
    private String modifiers;

    @Column(name = "signature", columnDefinition = "text")
    private String signature;

    @Column(name = "signature_hash", length = 64)
    private String signatureHash;

    @Column(name = "javadoc", columnDefinition = "text")
    private String javadoc;

    @Column(name = "parent_entity_id")
    private UUID parentEntityId;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    protected CodeEntity() {
    }

    public CodeEntity(UUID repositoryId, UUID analysisId, CodeEntityType entityType, String qualifiedName,
                      String simpleName, String filePath, Integer lineNumber, String modifiers,
                      String signature, String signatureHash, String javadoc, UUID parentEntityId,
                      String commitSha) {
        this.repositoryId = repositoryId;
        this.analysisId = analysisId;
        this.entityType = entityType;
        this.qualifiedName = qualifiedName;
        this.simpleName = simpleName;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.modifiers = modifiers;
        this.signature = signature;
        this.signatureHash = signatureHash;
        this.javadoc = javadoc;
        this.parentEntityId = parentEntityId;
        this.commitSha = commitSha;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public CodeEntityType getEntityType() {
        return entityType;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public String getModifiers() {
        return modifiers;
    }

    public String getSignature() {
        return signature;
    }

    public String getSignatureHash() {
        return signatureHash;
    }

    public String getJavadoc() {
        return javadoc;
    }

    public UUID getParentEntityId() {
        return parentEntityId;
    }

    public String getCommitSha() {
        return commitSha;
    }
}
