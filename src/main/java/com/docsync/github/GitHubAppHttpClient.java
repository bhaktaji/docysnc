package com.docsync.github;

import com.docsync.common.exception.ExternalServiceException;
import com.docsync.github.dto.GitHubFileChange;
import com.docsync.github.dto.GitHubRepositorySummary;
import com.docsync.github.dto.InstallationToken;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * GitHub App REST client.
 *
 * <p>App JWTs and installation tokens are only placed in request headers. They are never
 * included in exception messages or logs.</p>
 */
@Component
public class GitHubAppHttpClient implements GitHubAppClient {

    private static final String API_VERSION = "2022-11-28";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private static final Pattern FULL_NAME = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");

    private final RestClient restClient;
    private final GitHubRateLimitGuard rateLimitGuard;

    public GitHubAppHttpClient(RestClient.Builder builder,
                               GitHubRateLimitGuard rateLimitGuard,
                               @Value("${docsync.github.api-url:https://api.github.com}") String apiUrl) {
        this.rateLimitGuard = rateLimitGuard;
        this.restClient = builder
                .baseUrl(apiUrl)
                .requestInterceptor((request, body, execution) -> {
                    ClientHttpResponse response = execution.execute(request, body);
                    rateLimitGuard.record(response.getHeaders());
                    return response;
                })
                .build();
    }

    @Override
    public InstallationToken createInstallationToken(String appJwt, long installationId) {
        // Token creation is critical: it serves an in-flight onboarding request.
        rateLimitGuard.checkCritical();
        try {
            TokenResponse response = restClient.post()
                    .uri("/app/installations/{installationId}/access_tokens", installationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.token() == null || response.expiresAt() == null) {
                throw new ExternalServiceException("GitHub", "Installation token response was invalid");
            }
            return new InstallationToken(response.token(), response.expiresAt());
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("GitHub", "Failed to create installation token", ex);
        }
    }

    @Override
    public List<GitHubRepositorySummary> listInstallationRepositories(String installationToken) {
        List<GitHubRepositorySummary> repositories = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            rateLimitGuard.checkNonCritical();
            int currentPage = page;
            InstallationRepositoriesResponse response = get(
                    "/installation/repositories?per_page=" + PAGE_SIZE + "&page=" + currentPage,
                    installationToken,
                    InstallationRepositoriesResponse.class);

            if (response == null || response.repositories() == null) {
                break;
            }
            repositories.addAll(response.repositories());
            if (response.repositories().size() < PAGE_SIZE) {
                break;
            }
        }
        return repositories;
    }

    @Override
    public List<GitHubFileChange> compareCommits(String installationToken, String fullName,
                                                 String baseCommit, String headCommit) {
        if (fullName == null || !FULL_NAME.matcher(fullName).matches()
                || baseCommit == null || baseCommit.isBlank()
                || headCommit == null || headCommit.isBlank()) {
            return List.of();
        }
        rateLimitGuard.checkNonCritical();
        String[] parts = fullName.split("/", 2);
        try {
            CompareResponse response = restClient.get()
                    .uri("/repos/{owner}/{repo}/compare/{base}...{head}",
                            parts[0], parts[1], baseCommit, headCommit)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .retrieve()
                    .body(CompareResponse.class);

            if (response == null || response.files() == null) {
                return List.of();
            }
            return response.files();
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("GitHub", "Failed to compare commits", ex);
        }
    }

    @Override
    public List<String> listRepositoryTree(String installationToken, String fullName, String ref) {
        if (fullName == null || !FULL_NAME.matcher(fullName).matches() || ref == null || ref.isBlank()) {
            return List.of();
        }
        rateLimitGuard.checkNonCritical();
        String[] parts = fullName.split("/", 2);
        try {
            TreeResponse response = restClient.get()
                    .uri("/repos/{owner}/{repo}/git/trees/{ref}?recursive=1", parts[0], parts[1], ref)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .retrieve()
                    .body(TreeResponse.class);
            if (response == null || response.tree() == null) {
                return List.of();
            }
            return response.tree().stream()
                    .filter(entry -> "blob".equals(entry.type()))
                    .map(TreeEntry::path)
                    .filter(path -> path != null && !path.isBlank())
                    .toList();
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("GitHub", "Failed to list repository tree", ex);
        }
    }

    @Override
    public String getFileContent(String installationToken, String fullName, String path, String ref) {
        if (fullName == null || !FULL_NAME.matcher(fullName).matches() || path == null || path.isBlank()) {
            return null;
        }
        rateLimitGuard.checkNonCritical();
        String[] parts = fullName.split("/", 2);
        try {
            ContentResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/contents/{path}")
                            .queryParam("ref", ref)
                            .build(parts[0], parts[1], path))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .retrieve()
                    .body(ContentResponse.class);
            if (response == null || response.content() == null) {
                return null;
            }
            byte[] decoded = java.util.Base64.getMimeDecoder().decode(response.content().replaceAll("\\s", ""));
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("GitHub", "Failed to read file content", ex);
        }
    }

    @Override
    public GitHubRepositorySummary getRepository(String installationToken, String fullName) {
        if (fullName == null || !FULL_NAME.matcher(fullName).matches()) {
            throw new ExternalServiceException("GitHub", "Repository full name is invalid");
        }
        rateLimitGuard.checkNonCritical();
        String[] parts = fullName.split("/", 2);
        try {
            GitHubRepositorySummary repository = restClient.get()
                    .uri("/repos/{owner}/{repo}", parts[0], parts[1])
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .retrieve()
                    .body(GitHubRepositorySummary.class);

            if (repository == null || repository.id() == null) {
                throw new ExternalServiceException("GitHub", "Repository response was invalid");
            }
            return repository;
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("GitHub", "Failed to read repository metadata", ex);
        }
    }

    private <T> T get(String uri, String installationToken, Class<T> type) {
        rateLimitGuard.checkNonCritical();
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .retrieve()
                    .body(type);
        } catch (RuntimeException ex) {
            throw new ExternalServiceException("GitHub", "Failed to list installation repositories", ex);
        }
    }

    record TokenResponse(
            String token,
            @JsonProperty("expires_at") java.time.Instant expiresAt
    ) {
    }

    record InstallationRepositoriesResponse(
            @JsonProperty("total_count") int totalCount,
            List<GitHubRepositorySummary> repositories
    ) {
    }

    record CompareResponse(
            @JsonProperty("files") List<GitHubFileChange> files
    ) {
    }

    record TreeResponse(
            @JsonProperty("tree") List<TreeEntry> tree
    ) {
    }

    record TreeEntry(
            String path,
            String type
    ) {
    }

    record ContentResponse(
            String content
    ) {
    }
}
