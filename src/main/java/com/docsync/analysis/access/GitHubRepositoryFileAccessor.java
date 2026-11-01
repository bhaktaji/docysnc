package com.docsync.analysis.access;

import com.docsync.repository.entity.Repository;
import com.docsync.changedetection.model.ChangedFile;
import com.docsync.github.GitHubAppClient;
import com.docsync.github.InstallationTokenService;
import com.docsync.github.dto.GitHubFileChange;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GitHubRepositoryFileAccessor implements RepositoryFileAccessor {

    private final GitHubAppClient gitHubAppClient;
    private final InstallationTokenService tokenService;

    public GitHubRepositoryFileAccessor(GitHubAppClient gitHubAppClient, InstallationTokenService tokenService) {
        this.gitHubAppClient = gitHubAppClient;
        this.tokenService = tokenService;
    }

    @Override
    public List<String> listFiles(Repository repository, String commitSha) {
        if (repository == null) {
            return List.of();
        }
        String token = tokenService.getToken(repository.getInstallationId());
        return gitHubAppClient.listRepositoryTree(token, repository.getFullName(), commitSha);
    }

    @Override
    public String readFile(Repository repository, String path, String commitSha) {
        if (repository == null || path == null || path.isBlank()) {
            return null;
        }
        String token = tokenService.getToken(repository.getInstallationId());
        return gitHubAppClient.getFileContent(token, repository.getFullName(), path, commitSha);
    }

    @Override
    public List<ChangedFile> getChangedFiles(Repository repository, String baseCommit, String headCommit) {
        if (repository == null || baseCommit == null || baseCommit.isBlank()
                || headCommit == null || headCommit.isBlank() || baseCommit.equals(headCommit)) {
            return List.of();
        }
        String token = tokenService.getToken(repository.getInstallationId());
        List<GitHubFileChange> compared = gitHubAppClient.compareCommits(
                token, repository.getFullName(), baseCommit, headCommit);
        return compared.stream()
                .filter(change -> change != null && change.filename() != null)
                .map(change -> new ChangedFile(
                        change.filename(),
                        mapStatus(change.status()),
                        change.previousFilename(),
                        change.patch()))
                .toList();
    }

    private ChangedFile.ChangeStatus mapStatus(String status) {
        if (status == null) {
            return ChangedFile.ChangeStatus.MODIFIED;
        }
        return switch (status) {
            case "added" -> ChangedFile.ChangeStatus.ADDED;
            case "removed" -> ChangedFile.ChangeStatus.REMOVED;
            case "renamed" -> ChangedFile.ChangeStatus.RENAMED;
            default -> ChangedFile.ChangeStatus.MODIFIED;
        };
    }
}
