package com.docsync.webhook.dto;

/**
 * Acknowledgment returned to GitHub for every verified delivery.
 */
public record WebhookAcceptedResponse(boolean received, String eventId, String status) {
}
