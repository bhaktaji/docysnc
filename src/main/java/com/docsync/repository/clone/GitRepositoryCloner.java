package com.docsync.repository.clone;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Clones or fetches repository content into a confined per-repository workspace.
 */
public interface GitRepositoryCloner {

    CloneResult checkout(UUID repositoryId, String fullName, String branch, String installationToken);

    /** Best-effort workspace cleanup for a disconnected repository. */
    void deleteWorkspace(UUID repositoryId);

    record CloneResult(Path workspace, String commitSha) {
    }
}
