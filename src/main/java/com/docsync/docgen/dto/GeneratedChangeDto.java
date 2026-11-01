package com.docsync.docgen.dto;

import com.docsync.docgen.entity.ChangeStatus;
import com.docsync.docgen.entity.ChangeType;
import com.docsync.docgen.entity.GeneratedDocumentationChange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GeneratedChangeDto(
        UUID id,
        UUID repositoryId,
        UUID analysisId,
        UUID generationRunId,
        String filePath,
        ChangeType changeType,
        String originalContent,
        String generatedContent,
        String diffContent,
        String provenance,
        BigDecimal confidence,
        ChangeStatus status,
        int version,
        Instant createdAt
) {
    public static GeneratedChangeDto from(GeneratedDocumentationChange change) {
        return new GeneratedChangeDto(
                change.getId(),
                change.getRepositoryId(),
                change.getAnalysisId(),
                change.getGenerationRunId(),
                change.getFilePath(),
                change.getChangeType(),
                change.getOriginalContent(),
                change.getGeneratedContent(),
                change.getDiffContent(),
                change.getProvenance(),
                change.getConfidence(),
                change.getStatus(),
                change.getVersion(),
                change.getCreatedAt()
        );
    }
}
