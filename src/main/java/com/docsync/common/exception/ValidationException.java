package com.docsync.common.exception;

/**
 * Thrown when a business rule or validation fails.
 */
public class ValidationException extends DocSyncException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR", String.format("Validation failed for '%s': %s", field, message));
    }
}
