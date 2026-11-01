package com.docsync.webhook.exception;

import com.docsync.common.exception.DocSyncException;

/**
 * Raised when a webhook signature is missing or does not match the request body.
 */
public class WebhookSignatureException extends DocSyncException {

    public WebhookSignatureException() {
        super("ERR_WEBHOOK_SIGNATURE", "Webhook signature verification failed");
    }
}
