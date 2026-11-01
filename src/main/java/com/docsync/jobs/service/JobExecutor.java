package com.docsync.jobs.service;

import com.docsync.analysis.service.RepositoryAnalysisService;
import com.docsync.changedetection.model.ChangedFile;
import com.docsync.changedetection.service.ChangeDetectionService;
import com.docsync.pullrequest.entity.PullRequest;
import com.docsync.pullrequest.entity.PullRequestStatus;
import com.docsync.pullrequest.repository.PullRequestRepository;
import com.docsync.repository.entity.Repository;
import com.docsync.jobs.entity.AnalysisJob;
import com.docsync.repository.service.RepositoryService;
import com.docsync.webhook.entity.WebhookEvent;
import com.docsync.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);

    private final RepositoryService repositoryService;
    private final RepositoryAnalysisService analysisService;
    private final PullRequestRepository pullRequestRepository;
    private final ChangeDetectionService changeDetectionService;
    private final WebhookEventRepository webhookEventRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public JobExecutor(RepositoryService repositoryService,
                       RepositoryAnalysisService analysisService,
                       PullRequestRepository pullRequestRepository,
                       ChangeDetectionService changeDetectionService,
                       WebhookEventRepository webhookEventRepository,
                       Clock clock,
                       ObjectMapper objectMapper) {
        this.repositoryService = repositoryService;
        this.analysisService = analysisService;
        this.pullRequestRepository = pullRequestRepository;
        this.changeDetectionService = changeDetectionService;
        this.webhookEventRepository = webhookEventRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void execute(AnalysisJob job) {
        switch (job.getJobType()) {
            case PUSH_ANALYSIS -> executePush(job);
            case PULL_REQUEST_ANALYSIS -> executePullRequest(job);
            case REPOSITORY_SYNC -> {
                // Repository sync remains an on-demand operation handled by M2.
            }
        }
    }

    private void executePush(AnalysisJob job) {
        Repository repository = repositoryService.findRepositoryById(job.getRepositoryId())
                .orElseThrow(() -> new IllegalStateException("Repository not found: " + job.getRepositoryId()));

        String baseCommit = stringValue(job.getMetadata(), "baseCommit");
        if (baseCommit == null) {
            baseCommit = repository.getLastProcessedCommitSha();
            if (baseCommit == null) {
                baseCommit = repository.getLastCommitSha();
            }
        }
        String headCommit = job.getTriggerCommit();
        if (headCommit == null || headCommit.isBlank()) {
            throw new IllegalArgumentException("Push job is missing a head commit");
        }
        if (headCommit.equals(repository.getLastProcessedCommitSha())) {
            log.info("Skipping already processed commit {} for repository {}", headCommit, repository.getId());
            return;
        }

        JsonNode pushPayload = loadWebhookPayload(job.getWebhookEventId());
        List<ChangedFile> changedFiles = changeDetectionService.detectPushChanges(
                repository, baseCommit, headCommit, pushPayload);

        updateJobMetadata(job, changedFiles);
        analysisService.analyzeChangedFiles(repository.getId(), baseCommit, headCommit, job.getId());
        repository.setLastProcessedCommitSha(headCommit);
        repository.setLastProcessedAt(Instant.now(clock));
        repositoryService.save(repository);
        log.info("Processed push {} for repository {}: {} changed files",
                headCommit, repository.getId(), changedFiles.size());
    }

    private void executePullRequest(AnalysisJob job) {
        Repository repository = repositoryService.findRepositoryById(job.getRepositoryId())
                .orElseThrow(() -> new IllegalStateException("Repository not found: " + job.getRepositoryId()));

        JsonNode payload = loadWebhookPayload(job.getWebhookEventId());
        JsonNode pr = payload.path("pull_request");
        int prNumber = job.getPrNumber() == null ? pr.path("number").asInt() : job.getPrNumber();
        String headSha = firstNonBlank(job.getTriggerCommit(), pr.path("head").path("sha").asText(null));
        String baseSha = stringValue(job.getMetadata(), "baseCommit");
        if (baseSha == null) {
            baseSha = pr.path("base").path("sha").asText(null);
        }
        String title = pr.path("title").asText("Pull request " + prNumber);
        String author = pr.path("user").path("login").asText("unknown");
        String headBranch = pr.path("head").path("ref").asText("unknown");
        String baseBranch = pr.path("base").path("ref").asText("unknown");

        PullRequestStatus status = mapPullRequestStatus(payload.path("action").asText("opened"));
        PullRequest pullRequest = pullRequestRepository.findByRepositoryIdAndPrNumber(repository.getId(), prNumber)
                .orElse(null);
        if (pullRequest == null) {
            pullRequest = new PullRequest(repository.getId(), prNumber, title, author, status,
                    headBranch, baseBranch, headSha, baseSha);
        } else {
            pullRequest.setTitle(title);
            pullRequest.setStatus(status);
            pullRequest.setHeadSha(headSha);
            pullRequest.setBaseSha(baseSha);
        }
        pullRequest.setAnalysisJobId(job.getId());
        pullRequestRepository.save(pullRequest);

        List<ChangedFile> changedFiles = changeDetectionService.detectPullRequestChanges(
                repository, baseSha, headSha);
        updateJobMetadata(job, changedFiles);
        analysisService.analyzeChangedFiles(repository.getId(), baseSha, headSha, job.getId());
        log.info("Processed pull request #{} for repository {}: {} changed files",
                prNumber, repository.getId(), changedFiles.size());
    }

    private void updateJobMetadata(AnalysisJob job, List<ChangedFile> changedFiles) {
        ObjectNode metadata = job.getMetadata() != null && job.getMetadata().isObject()
                ? (ObjectNode) job.getMetadata().deepCopy()
                : objectMapper.createObjectNode();
        ArrayNode files = objectMapper.createArrayNode();
        for (ChangedFile file : changedFiles) {
            ObjectNode node = files.addObject();
            node.put("path", file.path());
            node.put("status", file.status().name());
            if (file.previousPath() != null) {
                node.put("previousPath", file.previousPath());
            }
        }
        metadata.set("changedFiles", files);
        job.setMetadata(metadata);
    }

    private JsonNode loadWebhookPayload(UUID webhookEventId) {
        if (webhookEventId == null) {
            return objectMapper.createObjectNode();
        }
        return webhookEventRepository.findById(webhookEventId)
                .map(WebhookEvent::getPayload)
                .orElse(objectMapper.createObjectNode());
    }

    private PullRequestStatus mapPullRequestStatus(String action) {
        if ("closed".equalsIgnoreCase(action)) {
            return PullRequestStatus.CLOSED;
        }
        return PullRequestStatus.OPEN;
    }

    private String stringValue(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
