package com.docsync.analysis.dto;

import com.docsync.analysis.entity.DocumentationPage;
import com.docsync.analysis.entity.DocumentationType;

import java.util.UUID;

public record DocumentationPageDetailDto(
        UUID id,
        UUID repositoryId,
        UUID analysisId,
        String title,
        DocumentationType type,
        String path,
        String contentHash,
        String lastAnalyzedCommit,
        String content
) {
    public static DocumentationPageDetailDto from(DocumentationPage page, String content) {
        return new DocumentationPageDetailDto(
                page.getId(),
                page.getRepositoryId(),
                page.getAnalysisId(),
                page.getTitle(),
                page.getType(),
                page.getPath(),
                page.getContentHash(),
                page.getLastAnalyzedCommit(),
                content
        );
    }
}
