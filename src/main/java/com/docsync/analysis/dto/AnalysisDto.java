package com.docsync.analysis.dto;

import com.docsync.analysis.entity.Analysis;
import com.docsync.analysis.entity.AnalysisScope;
import com.docsync.analysis.entity.AnalysisStatus;

import java.time.Instant;
import java.util.UUID;

public record AnalysisDto(
        UUID id,
        UUID repositoryId,
        UUID jobId,
        String branch,
        String commitSha,
        AnalysisScope scope,
        AnalysisStatus status,
        Object stats,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public static AnalysisDto from(Analysis analysis) {
        return new AnalysisDto(
                analysis.getId(),
                analysis.getRepositoryId(),
                analysis.getJobId(),
                analysis.getBranch(),
                analysis.getCommitSha(),
                analysis.getScope(),
                analysis.getStatus(),
                analysis.getStats(),
                analysis.getErrorMessage(),
                analysis.getCreatedAt(),
                analysis.getStartedAt(),
                analysis.getCompletedAt()
        );
    }
}
