package com.docsync.changedetection.service;

import com.docsync.changedetection.model.ChangedFile;
import com.docsync.github.InstallationTokenService;
import com.docsync.github.dto.GitHubFileChange;
import com.docsync.repository.entity.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ChangeDetectionServiceImpl implements ChangeDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ChangeDetectionServiceImpl.class);

    private final com.docsync.github.GitHubAppClient gitHubAppClient;
    private final InstallationTokenService tokenService;

    public ChangeDetectionServiceImpl(com.docsync.github.GitHubAppClient gitHubAppClient,
                                     InstallationTokenService tokenService) {
        this.gitHubAppClient = gitHubAppClient;
        this.tokenService = tokenService;
    }

    @Override
    public List<ChangedFile> detectPushChanges(Repository repository, String baseCommit,
                                               String headCommit, JsonNode pushPayload) {
        if (repository == null || headCommit == null || headCommit.isBlank()) {
            return List.of();
        }
        if (baseCommit != null && !baseCommit.isBlank() && !isZeroCommit(baseCommit)
                && !baseCommit.equals(headCommit)) {
            try {
                String token = tokenService.getToken(repository.getInstallationId());
                List<GitHubFileChange> compared = gitHubAppClient.compareCommits(
                        token, repository.getFullName(), baseCommit, headCommit);
                if (!compared.isEmpty()) {
                    return mapCompared(compared);
                }
            } catch (RuntimeException ex) {
                log.warn("GitHub compare failed for {}; falling back to push payload", repository.getFullName());
            }
        }
        return fromPushPayload(pushPayload);
    }

    @Override
    public List<ChangedFile> detectPullRequestChanges(Repository repository, String baseCommit,
                                                     String headCommit) {
        if (repository == null || baseCommit == null || baseCommit.isBlank()
                || headCommit == null || headCommit.isBlank() || baseCommit.equals(headCommit)) {
            return List.of();
        }
        try {
            String token = tokenService.getToken(repository.getInstallationId());
            List<GitHubFileChange> compared = gitHubAppClient.compareCommits(
                    token, repository.getFullName(), baseCommit, headCommit);
            return mapCompared(compared);
        } catch (RuntimeException ex) {
            log.warn("GitHub compare failed for PR on {}", repository.getFullName());
            return List.of();
        }
    }

    private List<ChangedFile> mapCompared(List<GitHubFileChange> compared) {
        List<ChangedFile> result = new ArrayList<>();
        for (GitHubFileChange file : compared) {
            if (file == null || file.filename() == null) {
                continue;
            }
            ChangedFile.ChangeStatus status = switch (file.status() == null ? "" : file.status()) {
                case "added" -> ChangedFile.ChangeStatus.ADDED;
                case "removed" -> ChangedFile.ChangeStatus.REMOVED;
                case "renamed" -> ChangedFile.ChangeStatus.RENAMED;
                default -> ChangedFile.ChangeStatus.MODIFIED;
            };
            result.add(new ChangedFile(file.filename(), status, file.previousFilename(), file.patch()));
        }
        return result;
    }

    private List<ChangedFile> fromPushPayload(JsonNode pushPayload) {
        if (pushPayload == null || !pushPayload.isObject()) {
            return List.of();
        }
        Set<String> added = new HashSet<>();
        Set<String> modified = new HashSet<>();
        Set<String> removed = new HashSet<>();
        JsonNode commits = pushPayload.path("commits");
        if (commits.isArray()) {
            for (JsonNode commit : commits) {
                collect(commit.path("added"), added);
                collect(commit.path("modified"), modified);
                collect(commit.path("removed"), removed);
            }
        }

        List<ChangedFile> result = new ArrayList<>();
        Set<String> consumedRemoved = new HashSet<>();
        for (String addedPath : added) {
            String matchedRemoved = removed.stream()
                    .filter(path -> !consumedRemoved.contains(path)
                            && fileName(path).equals(fileName(addedPath)))
                    .findFirst()
                    .orElse(null);
            if (matchedRemoved != null) {
                consumedRemoved.add(matchedRemoved);
                result.add(new ChangedFile(addedPath, ChangedFile.ChangeStatus.RENAMED,
                        matchedRemoved, null));
            } else {
                result.add(new ChangedFile(addedPath, ChangedFile.ChangeStatus.ADDED, null, null));
            }
        }
        for (String path : modified) {
            result.add(new ChangedFile(path, ChangedFile.ChangeStatus.MODIFIED, null, null));
        }
        for (String path : removed) {
            if (!consumedRemoved.contains(path)) {
                result.add(new ChangedFile(path, ChangedFile.ChangeStatus.REMOVED, null, null));
            }
        }
        return result;
    }

    private void collect(JsonNode node, Set<String> target) {
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    target.add(item.asText());
                }
            }
        }
    }

    private String fileName(String path) {
        if (path == null) {
            return "";
        }
        int index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    private boolean isZeroCommit(String commit) {
        return commit.chars().allMatch(ch -> ch == '0');
    }
}
