package com.docsync.github;

import com.docsync.github.dto.GitHubRepositorySummary;
import com.docsync.github.dto.InstallationToken;

import java.util.List;

/**
 * Minimal GitHub App API surface used by DocSync. Implementations must never log tokens
 * or the App private key.
 */
public interface GitHubAppClient {

    InstallationToken createInstallationToken(String appJwt, long installationId);

    List<GitHubRepositorySummary> listInstallationRepositories(String installationToken);

    GitHubRepositorySummary getRepository(String installationToken, String fullName);

    List<com.docsync.github.dto.GitHubFileChange> compareCommits(
            String installationToken, String fullName, String baseCommit, String headCommit);

    List<String> listRepositoryTree(String installationToken, String fullName, String ref);

    String getFileContent(String installationToken, String fullName, String path, String ref);
}
