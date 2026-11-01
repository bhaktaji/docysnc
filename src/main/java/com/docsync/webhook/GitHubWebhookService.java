package com.docsync.webhook;

import com.docsync.connection.service.RepositoryConnectionService;
import com.docsync.jobs.service.JobService;
import com.docsync.repository.entity.Repository;
import com.docsync.repository.entity.RepositoryStatus;
import com.docsync.repository.service.RepositoryService;
import com.docsync.webhook.dto.WebhookAcceptedResponse;
import com.docsync.webhook.entity.WebhookEvent;
import com.docsync.webhook.entity.WebhookProcessingStatus;
import com.docsync.webhook.exception.InvalidWebhookException;
import com.docsync.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Verifies, deduplicates, persists, and dispatches GitHub webhook deliveries.
 *
 * <p>Delivery IDs are unique, so GitHub retries are idempotent. Processing is intentionally
 * not wrapped in one long transaction: a failed handler rolls back only its own work, while
 * the delivery record remains and can be marked {@code FAILED} for inspection.</p>
 */
@Service
public class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);
    private static final String PROVIDER = "GITHUB";
    private static final int MAX_ERROR_LENGTH = 500;

    private final GitHubWebhookVerifier verifier;
    private final WebhookEventRepository eventRepository;
    private final RepositoryConnectionService connectionService;
    private final RepositoryService repositoryService;
    private final JobService jobService;
    private final ObjectMapper objectMapper;

    public GitHubWebhookService(GitHubWebhookVerifier verifier,
                                WebhookEventRepository eventRepository,
                                RepositoryConnectionService connectionService,
                                RepositoryService repositoryService,
                                JobService jobService,
                                ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.eventRepository = eventRepository;
        this.connectionService = connectionService;
        this.repositoryService = repositoryService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    public WebhookAcceptedResponse handle(String eventType, String deliveryId,
                                          String signature, byte[] body) {
        verifier.verify(body, signature);

        if (eventType == null || eventType.isBlank()) {
            throw new InvalidWebhookException("X-GitHub-Event header is required");
        }
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new InvalidWebhookException("X-GitHub-Delivery header is required");
        }

        WebhookEvent existing = eventRepository
                .findByProviderAndDeliveryId(PROVIDER, deliveryId)
                .orElse(null);
        if (existing != null) {
            return response(existing);
        }

        JsonNode payload = parse(body);
        WebhookEvent event = new WebhookEvent(deliveryId, eventType, payload);
        try {
            eventRepository.save(event);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent retry won the insert race; return the stored delivery.
            return eventRepository.findByProviderAndDeliveryId(PROVIDER, deliveryId)
                    .map(this::response)
                    .orElseThrow(() -> ex);
        }

        try {
            boolean handled = dispatch(eventType, payload, event);
            event.markProcessed(handled
                    ? WebhookProcessingStatus.PROCESSED
                    : WebhookProcessingStatus.IGNORED);
        } catch (RuntimeException ex) {
            event.markProcessed(WebhookProcessingStatus.FAILED);
            event.setErrorMessage(truncate(ex.getMessage()));
            log.warn("Webhook delivery {} ({}) failed: {}", deliveryId, eventType, ex.getMessage());
        }

        eventRepository.save(event);
        return response(event);
    }

    private boolean dispatch(String eventType, JsonNode payload, WebhookEvent event) {
        return switch (eventType) {
            case "installation" -> {
                connectionService.handleInstallationEvent(payload.path("action").asText(null), payload);
                yield true;
            }
            case "installation_repositories" -> {
                connectionService.handleInstallationRepositoriesEvent(
                        payload.path("action").asText(null), payload);
                yield true;
            }
            case "push" -> handlePushEvent(payload, event);
            case "pull_request" -> handlePullRequestEvent(payload, event);
            default -> {
                log.debug("Stored webhook event {} for later processing", eventType);
                yield false;
            }
        };
    }

    private boolean handlePushEvent(JsonNode payload, WebhookEvent event) {
        long installationId = payload.path("installation").path("id").asLong(0);
        long externalId = payload.path("repository").path("id").asLong(0);
        if (installationId <= 0 || externalId <= 0) {
            return false;
        }
        Repository repository = repositoryService
                .findRepositoryByInstallationAndExternalId(installationId, externalId)
                .orElse(null);
        if (repository == null || repository.getStatus() == RepositoryStatus.ARCHIVED) {
            return false;
        }

        String ref = payload.path("ref").asText("");
        if (!ref.equals("refs/heads/" + repository.getDefaultBranch())) {
            log.debug("Ignoring push to non-monitored branch {} for repository {}", ref, repository.getId());
            return false;
        }

        String headCommit = payload.path("after").asText(null);
        if (headCommit == null || headCommit.isBlank() || headCommit.chars().allMatch(ch -> ch == '0')) {
            return false;
        }
        String baseCommit = payload.path("before").asText(null);
        if (baseCommit != null && baseCommit.chars().allMatch(ch -> ch == '0')) {
            baseCommit = null;
        }

        event.setRepositoryId(repository.getId());
        jobService.enqueuePush(repository.getId(), event.getId(), headCommit, baseCommit, ref, 0);
        return true;
    }

    private boolean handlePullRequestEvent(JsonNode payload, WebhookEvent event) {
        String action = payload.path("action").asText("");
        if (!"opened".equals(action) && !"synchronize".equals(action) && !"reopened".equals(action)) {
            return false;
        }

        long installationId = payload.path("installation").path("id").asLong(0);
        long externalId = payload.path("repository").path("id").asLong(0);
        if (installationId <= 0 || externalId <= 0) {
            return false;
        }
        Repository repository = repositoryService
                .findRepositoryByInstallationAndExternalId(installationId, externalId)
                .orElse(null);
        if (repository == null || repository.getStatus() == RepositoryStatus.ARCHIVED) {
            return false;
        }

        JsonNode pr = payload.path("pull_request");
        int prNumber = pr.path("number").asInt(0);
        if (prNumber <= 0) {
            return false;
        }
        String headCommit = pr.path("head").path("sha").asText(null);
        if (headCommit == null || headCommit.isBlank()) {
            return false;
        }
        String baseCommit = pr.path("base").path("sha").asText(null);

        event.setRepositoryId(repository.getId());
        jobService.enqueuePullRequest(repository.getId(), event.getId(), prNumber, headCommit, baseCommit, 0);
        return true;
    }

    private JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw new InvalidWebhookException("Webhook payload is empty");
        }
        try {
            JsonNode payload = objectMapper.readTree(body);
            if (payload == null || !payload.isObject()) {
                throw new InvalidWebhookException("Webhook payload must be a JSON object");
            }
            return payload;
        } catch (IOException ex) {
            throw new InvalidWebhookException("Webhook payload is not valid JSON");
        }
    }

    private WebhookAcceptedResponse response(WebhookEvent event) {
        return new WebhookAcceptedResponse(true, event.getDeliveryId(),
                event.getProcessingStatus().name());
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Webhook processing failed";
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
