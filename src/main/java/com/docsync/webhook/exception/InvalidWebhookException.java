package com.docsync.webhook.exception;

import com.docsync.common.exception.DocSyncException;

/**
 * Raised when a signed webhook is missing required headers or has an unreadable payload.
 */
public class InvalidWebhookException extends DocSyncException {

    public InvalidWebhookException(String message) {
        super("ERR_WEBHOOK_INVALID", message);
    }
}
