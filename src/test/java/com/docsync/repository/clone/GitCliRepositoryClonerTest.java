package com.docsync.repository.clone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitCliRepositoryClonerTest {

    @TempDir
    Path workspace;

    @Test
    void rejectsUnsafeRepositoryNamesBeforeStartingGit() {
        GitCliRepositoryCloner cloner = new GitCliRepositoryCloner(
                workspace.toString(), "github.com", 5);

        assertThrows(GitOperationException.class,
                () -> cloner.checkout(UUID.randomUUID(), "owner/repo; rm -rf /", "main", "token"));
        assertThrows(GitOperationException.class,
                () -> cloner.checkout(UUID.randomUUID(), "https://evil.example/repo", "main", "token"));
        assertThrows(GitOperationException.class,
                () -> cloner.checkout(UUID.randomUUID(), "owner", "main", "token"));
        assertThrows(GitOperationException.class,
                () -> cloner.checkout(null, "owner/repo", "main", "token"));
    }

    @Test
    void rejectsUnsafeBranchNames() {
        GitCliRepositoryCloner cloner = new GitCliRepositoryCloner(
                workspace.toString(), "github.com", 5);

        assertThrows(GitOperationException.class,
                () -> cloner.checkout(UUID.randomUUID(), "owner/repo", "../main", "token"));
        assertThrows(GitOperationException.class,
                () -> cloner.checkout(UUID.randomUUID(), "owner/repo", "main --upload-pack=evil", "token"));
    }

    @Test
    void failureMessageNeverContainsTheInstallationToken() {
        GitCliRepositoryCloner cloner = new GitCliRepositoryCloner(
                workspace.toString(), "127.0.0.1", 3);

        GitOperationException error = assertThrows(GitOperationException.class,
                () -> cloner.checkout(UUID.randomUUID(), "owner/repo", "main", "ghs_super_secret_token"));

        assertFalse(error.getMessage().contains("ghs_super_secret_token"));
        assertFalse(error.getMessage().contains("AUTHORIZATION"));
    }

    @Test
    void missingWorkspaceIsDeletedIdempotently() {
        GitCliRepositoryCloner cloner = new GitCliRepositoryCloner(
                workspace.toString(), "github.com", 5);

        assertDoesNotThrow(() -> cloner.deleteWorkspace(UUID.randomUUID()));
        assertDoesNotThrow(() -> cloner.deleteWorkspace(UUID.randomUUID()));
    }

    @Test
    void workspaceRootIsCreated() {
        Path nested = workspace.resolve("nested").resolve("workspaces");
        GitCliRepositoryCloner cloner = new GitCliRepositoryCloner(
                nested.toString(), "github.com", 5);

        assertDoesNotThrow(() -> cloner.deleteWorkspace(UUID.randomUUID()));
        org.junit.jupiter.api.Assertions.assertTrue(Files.isDirectory(nested));
    }
}
