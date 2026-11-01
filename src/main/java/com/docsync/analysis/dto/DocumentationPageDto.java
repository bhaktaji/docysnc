package com.docsync.analysis.dto;

import com.docsync.analysis.entity.DocumentationPage;
import com.docsync.analysis.entity.DocumentationType;

import java.util.UUID;

public record DocumentationPageDto(
        UUID id,
        UUID repositoryId,
        UUID analysisId,
        String title,
        DocumentationType type,
        String path,
        String contentHash,
        String lastAnalyzedCommit
) {
    public static DocumentationPageDto from(DocumentationPage page) {
        return new DocumentationPageDto(
                page.getId(),
                page.getRepositoryId(),
                page.getAnalysisId(),
                page.getTitle(),
                page.getType(),
                page.getPath(),
                page.getContentHash(),
                page.getLastAnalyzedCommit()
        );
    }
}
