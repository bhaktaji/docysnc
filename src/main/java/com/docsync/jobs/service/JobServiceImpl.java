package com.docsync.jobs.service;

import com.docsync.common.exception.DuplicateResourceException;
import com.docsync.jobs.entity.AnalysisJob;
import com.docsync.jobs.entity.JobStatus;
import com.docsync.jobs.entity.JobType;
import com.docsync.jobs.repository.AnalysisJobRepository;
import com.docsync.webhook.entity.WebhookEvent;
import com.docsync.webhook.entity.WebhookProcessingStatus;
import com.docsync.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class JobServiceImpl implements JobService {

    private static final int[] RETRY_DELAYS_SECONDS = {60, 300, 900};

    private final AnalysisJobRepository jobRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public JobServiceImpl(AnalysisJobRepository jobRepository, WebhookEventRepository webhookEventRepository,
                          Clock clock, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisJob enqueuePush(UUID repositoryId, UUID webhookEventId, String triggerCommit,
                                   String baseCommit, String ref, int priority) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("baseCommit", baseCommit);
        metadata.put("headCommit", triggerCommit);
        metadata.put("ref", ref);

        AnalysisJob job = new AnalysisJob(repositoryId, webhookEventId, JobType.PUSH_ANALYSIS,
                triggerCommit, null, metadata, priority);
        return saveOrFindExisting(job, repositoryId, JobType.PUSH_ANALYSIS, triggerCommit, null);
    }

    @Override
    public AnalysisJob enqueuePullRequest(UUID repositoryId, UUID webhookEventId, int prNumber,
                                          String triggerCommit, String baseCommit, int priority) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("baseCommit", baseCommit);
        metadata.put("headCommit", triggerCommit);
        metadata.put("prNumber", prNumber);

        AnalysisJob job = new AnalysisJob(repositoryId, webhookEventId, JobType.PULL_REQUEST_ANALYSIS,
                triggerCommit, prNumber, metadata, priority);
        return saveOrFindExisting(job, repositoryId, JobType.PULL_REQUEST_ANALYSIS, triggerCommit, prNumber);
    }

    private AnalysisJob saveOrFindExisting(AnalysisJob job, UUID repositoryId, JobType type,
                                           String triggerCommit, Integer prNumber) {
        try {
            return jobRepository.save(job);
        } catch (DataIntegrityViolationException ex) {
            return jobRepository
                    .findFirstByRepositoryIdAndJobTypeAndTriggerCommitAndPrNumberOrderByCreatedAtDesc(
                            repositoryId, type, triggerCommit, prNumber)
                    .orElseThrow(() -> ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisJob> pollNext() {
        Optional<AnalysisJob> claimed = jobRepository.findNextQueuedJob(Instant.now(clock));
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        AnalysisJob job = claimed.get();
        job.setStatus(JobStatus.RUNNING);
        job.setLockedBy(workerId());
        job.setLockedAt(Instant.now(clock));
        job.setStartedAt(Instant.now(clock));
        job.setNextAttemptAt(null);
        return Optional.of(jobRepository.save(job));
    }

    @Override
    public void markCompleted(UUID jobId) {
        AnalysisJob job = require(jobId);
        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(Instant.now(clock));
        job.setLockedBy(null);
        job.setLockedAt(null);
        job.setErrorMessage(null);
        jobRepository.save(job);
        updateWebhookEvent(job.getWebhookEventId(), WebhookProcessingStatus.PROCESSED);
    }

    @Override
    public void markFailed(UUID jobId, String errorMessage) {
        AnalysisJob job = require(jobId);
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(truncate(errorMessage));
        job.setLockedBy(null);
        job.setLockedAt(null);

        if (job.getRetryCount() >= job.getMaxRetries()) {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(Instant.now(clock));
            updateWebhookEvent(job.getWebhookEventId(), WebhookProcessingStatus.FAILED);
        } else {
            job.setStatus(JobStatus.QUEUED);
            int delay = RETRY_DELAYS_SECONDS[Math.min(job.getRetryCount() - 1,
                    RETRY_DELAYS_SECONDS.length - 1)];
            job.setNextAttemptAt(Instant.now(clock).plus(Duration.ofSeconds(delay)));
        }
        jobRepository.save(job);
    }

    @Override
    public int requeueStaleJobs(Instant threshold) {
        int count = 0;
        for (AnalysisJob job : jobRepository.findAllByStatusAndLockedAtBefore(JobStatus.RUNNING, threshold)) {
            job.setRetryCount(job.getRetryCount() + 1);
            if (job.getRetryCount() >= job.getMaxRetries()) {
                job.setStatus(JobStatus.FAILED);
                job.setCompletedAt(Instant.now(clock));
                job.setErrorMessage(job.getErrorMessage() == null
                        ? "Job abandoned by worker"
                        : job.getErrorMessage());
            } else {
                job.setStatus(JobStatus.QUEUED);
                job.setNextAttemptAt(Instant.now(clock));
            }
            job.setLockedBy(null);
            job.setLockedAt(null);
            jobRepository.save(job);
            count++;
        }
        return count;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisJob> getJob(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisJob> listJobsForRepository(UUID repositoryId) {
        return jobRepository.findAllByRepositoryIdOrderByCreatedAtDesc(repositoryId);
    }

    private AnalysisJob require(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId));
    }

    private String workerId() {
        return "worker-" + ProcessHandle.current().pid();
    }

    private void updateWebhookEvent(UUID webhookEventId, WebhookProcessingStatus status) {
        if (webhookEventId == null) {
            return;
        }
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.markProcessed(status);
            if (status == WebhookProcessingStatus.FAILED) {
                event.setErrorMessage("Job processing failed");
            }
            webhookEventRepository.save(event);
        });
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Job failed";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
