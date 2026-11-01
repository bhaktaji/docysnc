package com.docsync.repository.entity;

/**
 * Repository processing lifecycle (FR-004).
 *
 * <p>PENDING → CLONING → INDEXING → READY, or ERROR at any stage. ARCHIVED means the
 * repository was disconnected or its installation was revoked.</p>
 */
public enum RepositoryStatus {
    PENDING,
    CLONING,
    INDEXING,
    READY,
    ERROR,
    ARCHIVED
}
