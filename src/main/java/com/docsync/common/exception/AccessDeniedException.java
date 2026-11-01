package com.docsync.common.exception;

/**
 * Thrown when the caller does not have permission to access a resource.
 */
public class AccessDeniedException extends DocSyncException {

    public AccessDeniedException(String message) {
        super("ACCESS_DENIED", message);
    }

    public AccessDeniedException(String resourceType, String resourceId) {
        super("ACCESS_DENIED",
                String.format("Access denied to %s: %s", resourceType, resourceId));
    }
}
