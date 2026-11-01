package com.docsync.jobs.service;

import com.docsync.jobs.entity.AnalysisJob;
import com.docsync.jobs.entity.JobType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobService {

    AnalysisJob enqueuePush(UUID repositoryId, UUID webhookEventId, String triggerCommit,
                           String baseCommit, String ref, int priority);

    AnalysisJob enqueuePullRequest(UUID repositoryId, UUID webhookEventId, int prNumber,
                                  String triggerCommit, String baseCommit, int priority);

    Optional<AnalysisJob> pollNext();

    void markCompleted(UUID jobId);

    void markFailed(UUID jobId, String errorMessage);

    int requeueStaleJobs(Instant threshold);

    Optional<AnalysisJob> getJob(UUID jobId);

    List<AnalysisJob> listJobsForRepository(UUID repositoryId);
}
