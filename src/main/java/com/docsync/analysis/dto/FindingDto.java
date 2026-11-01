package com.docsync.analysis.dto;

import com.docsync.analysis.entity.Finding;
import com.docsync.analysis.entity.FindingCategory;
import com.docsync.analysis.entity.FindingSeverity;
import com.docsync.analysis.entity.FindingStatus;

import java.time.Instant;
import java.util.UUID;

public record FindingDto(
        UUID id,
        UUID repositoryId,
        UUID analysisId,
        FindingSeverity severity,
        FindingCategory category,
        String title,
        String description,
        UUID entityId,
        String filePath,
        Integer lineNumber,
        FindingStatus status,
        Instant createdAt
) {
    public static FindingDto from(Finding finding) {
        return new FindingDto(
                finding.getId(),
                finding.getRepositoryId(),
                finding.getAnalysisId(),
                finding.getSeverity(),
                finding.getCategory(),
                finding.getTitle(),
                finding.getDescription(),
                finding.getEntityId(),
                finding.getFilePath(),
                finding.getLineNumber(),
                finding.getStatus(),
                finding.getCreatedAt()
        );
    }
}
