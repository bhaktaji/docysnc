package com.docsync.connection.entity;

/**
 * Status of a GitHub App installation connection.
 */
public enum ConnectionStatus {
    /** Installation received but not yet linked to an organization. */
    PENDING,
    ACTIVE,
    SUSPENDED,
    REVOKED,
    ERROR
}
