package com.docsync.changedetection.model;

public record ChangedFile(
        String path,
        ChangeStatus status,
        String previousPath,
        String patch
) {
    public enum ChangeStatus {
        ADDED,
        MODIFIED,
        REMOVED,
        RENAMED
    }
}
