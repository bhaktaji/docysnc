package com.docsync.webhook.entity;

import com.docsync.common.entity.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted GitHub webhook delivery. The unique (provider, delivery_id) constraint
 * makes retries idempotent.
 */
@Entity
@Table(name = "webhook_events")
public class WebhookEvent extends BaseEntity {

    @Column(name = "provider", nullable = false, length = 20)
    private String provider = "GITHUB";

    @Column(name = "delivery_id", nullable = false, length = 255)
    private String deliveryId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "repository_id")
    private UUID repositoryId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private WebhookProcessingStatus processingStatus = WebhookProcessingStatus.RECEIVED;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookEvent() {
    }

    public WebhookEvent(String deliveryId, String eventType, JsonNode payload) {
        this.provider = "GITHUB";
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.payload = payload;
        this.processingStatus = WebhookProcessingStatus.RECEIVED;
        this.receivedAt = Instant.now();
    }

    public String getProvider() {
        return provider;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(UUID repositoryId) {
        this.repositoryId = repositoryId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public WebhookProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(WebhookProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void markProcessed(WebhookProcessingStatus status) {
        this.processingStatus = status;
        this.processedAt = Instant.now();
    }
}
