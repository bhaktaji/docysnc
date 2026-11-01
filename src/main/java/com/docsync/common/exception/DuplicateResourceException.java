package com.docsync.common.exception;

/**
 * Thrown when a duplicate resource is detected.
 */
public class DuplicateResourceException extends DocSyncException {

    public DuplicateResourceException(String resourceType, String identifier) {
        super("DUPLICATE_RESOURCE",
                String.format("%s already exists: %s", resourceType, identifier));
    }
}
