package com.docsync.jobs.entity;

public enum JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    DEAD_LETTER
}
