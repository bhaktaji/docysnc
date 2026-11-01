package com.docsync.jobs.dto;

import com.docsync.jobs.entity.JobStatus;
import com.docsync.jobs.entity.JobType;

import java.time.Instant;
import java.util.UUID;

public record JobDto(
        UUID id,
        UUID repositoryId,
        UUID webhookEventId,
        JobType type,
        JobStatus status,
        int priority,
        String triggerCommit,
        Integer prNumber,
        String errorMessage,
        int retryCount,
        int maxRetries,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public static JobDto from(com.docsync.jobs.entity.AnalysisJob job) {
        return new JobDto(
                job.getId(),
                job.getRepositoryId(),
                job.getWebhookEventId(),
                job.getJobType(),
                job.getStatus(),
                job.getPriority(),
                job.getTriggerCommit(),
                job.getPrNumber(),
                job.getErrorMessage(),
                job.getRetryCount(),
                job.getMaxRetries(),
                job.getNextAttemptAt(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }
}
