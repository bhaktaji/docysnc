package com.docsync.repository.clone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Clones and fetches repositories with the system {@code git} binary.
 *
 * <p>Security properties:</p>
 * <ul>
 *   <li>Only HTTPS URLs on the configured host are constructed; repository names are
 *       validated so they cannot inject URL components.</li>
 *   <li>The installation token is passed through git's {@code http.extraheader}
 *       environment configuration, never on the command line.</li>
 *   <li>Interactive credential prompts are disabled.</li>
 *   <li>Each repository is confined to {@code workspaceRoot/<repositoryId>}; paths are
 *       normalized and checked before any file operation.</li>
 *   <li>Commands run with a timeout and a captured, sanitized log. Repository code is
 *       never executed.</li>
 * </ul>
 */
@Component
public class GitCliRepositoryCloner implements GitRepositoryCloner {

    private static final Logger log = LoggerFactory.getLogger(GitCliRepositoryCloner.class);

    private static final Pattern FULL_NAME = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9.-]+$");
    private static final int MAX_OUTPUT_BYTES = 8 * 1024;
    private static final int MAX_ERROR_CHARS = 300;

    private final Path workspaceRoot;
    private final String cloneHost;
    private final Duration timeout;

    public GitCliRepositoryCloner(
            @Value("${docsync.repository.workspace-root:./data/workspaces}") String workspaceRoot,
            @Value("${docsync.repository.clone-host:github.com}") String cloneHost,
            @Value("${docsync.repository.git-timeout-seconds:300}") long timeoutSeconds) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        if (cloneHost == null || !HOST.matcher(cloneHost).matches()) {
            throw new IllegalStateException("docsync.repository.clone-host is invalid");
        }
        this.cloneHost = cloneHost;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    @Override
    public CloneResult checkout(UUID repositoryId, String fullName, String branch, String installationToken) {
        if (repositoryId == null) {
            throw new GitOperationException("Repository ID is required for checkout");
        }
        if (fullName == null || !FULL_NAME.matcher(fullName).matches()) {
            throw new GitOperationException("Repository name is invalid");
        }

        String safeBranch = branch == null || branch.isBlank() ? "main" : branch;
        if (!safeBranch.matches("^[A-Za-z0-9._/-]+$") || safeBranch.contains("..")) {
            throw new GitOperationException("Repository branch name is invalid");
        }

        Path root = ensureRoot();
        Path directory = repositoryDirectory(root, repositoryId);
        String url = "https://" + cloneHost + "/" + fullName + ".git";
        boolean existing = Files.isDirectory(directory.resolve(".git"));

        try {
            if (existing) {
                run(List.of("git", "-C", directory.toString(), "remote", "set-url", "origin", url),
                        root, installationToken);
                run(List.of("git", "-C", directory.toString(), "fetch", "--depth", "1", "--no-tags",
                        "origin", safeBranch), root, installationToken);
                run(List.of("git", "-C", directory.toString(), "reset", "--hard", "FETCH_HEAD"),
                        root, installationToken);
            } else {
                deleteRecursively(directory);
                run(List.of("git", "clone", "--depth", "1", "--single-branch", "--no-tags",
                        "--branch", safeBranch, url, directory.toString()), root, installationToken);
            }

            String commitSha = run(List.of("git", "-C", directory.toString(), "rev-parse", "HEAD"),
                    root, installationToken).trim();
            return new CloneResult(directory, commitSha);
        } catch (GitOperationException ex) {
            if (!existing) {
                deleteRecursively(directory);
            }
            log.warn("Git checkout failed for repository {}: {}", fullName, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void deleteWorkspace(UUID repositoryId) {
        try {
            deleteRecursively(repositoryDirectory(ensureRoot(), repositoryId));
        } catch (RuntimeException ex) {
            log.warn("Unable to clean workspace for repository {}: {}", repositoryId, ex.getMessage());
        }
    }

    private Path ensureRoot() {
        try {
            Files.createDirectories(workspaceRoot);
            return workspaceRoot;
        } catch (IOException ex) {
            throw new GitOperationException("Repository workspace is not writable", ex);
        }
    }

    private Path repositoryDirectory(Path root, UUID repositoryId) {
        Path directory = root.resolve(repositoryId.toString()).normalize();
        if (!directory.startsWith(root)) {
            throw new GitOperationException("Repository workspace path is outside the workspace root");
        }
        return directory;
    }

    /**
     * Runs a git command with a timeout. Output is written to a temporary file so a chatty
     * command cannot block on a full pipe; only a capped, token-redacted snippet is surfaced.
     */
    private String run(List<String> command, Path workingDirectory, String token) {
        Path logFile = null;
        Process process = null;
        try {
            logFile = Files.createTempFile(workspaceRoot, ".git-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            builder.environment().put("GCM_INTERACTIVE", "Never");
            builder.environment().put("GIT_CONFIG_COUNT", "1");
            builder.environment().put("GIT_CONFIG_KEY_0",
                    "http.https://" + cloneHost + "/.extraheader");
            builder.environment().put("GIT_CONFIG_VALUE_0",
                    "AUTHORIZATION: basic " + base64("x-access-token:" + token));

            process = builder.start();
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new GitOperationException("Git operation timed out");
            }

            String output = readCapped(logFile);
            if (process.exitValue() != 0) {
                throw new GitOperationException(failureMessage(redact(output, token)));
            }
            return output;
        } catch (IOException ex) {
            throw new GitOperationException("Git is not available on this host", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new GitOperationException("Git operation was interrupted");
        } finally {
            if (logFile != null) {
                try {
                    Files.deleteIfExists(logFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup only.
                }
            }
        }
    }

    private String readCapped(Path logFile) throws IOException {
        try (InputStream input = Files.newInputStream(logFile, StandardOpenOption.READ)) {
            byte[] bytes = input.readNBytes(MAX_OUTPUT_BYTES);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private String redact(String output, String token) {
        if (output == null) {
            return "";
        }
        String redacted = output;
        if (token != null && !token.isBlank()) {
            redacted = redacted.replace(token, "***");
        }
        redacted = redacted.replaceAll("(?i)authorization:\\s*basic\\s+[A-Za-z0-9+/=]+", "AUTHORIZATION: ***");
        return redacted;
    }

    private String failureMessage(String output) {
        String cleaned = output == null ? "" : output.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_ERROR_CHARS) {
            cleaned = cleaned.substring(0, MAX_ERROR_CHARS) + "...";
        }
        return cleaned.isBlank() ? "Git operation failed" : "Git operation failed: " + cleaned;
    }

    private void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> ordered = new ArrayList<>(paths.toList());
            ordered.sort(Comparator.reverseOrder());
            for (Path path : ordered) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            log.warn("Unable to remove workspace directory {}: {}", directory.getFileName(), ex.getMessage());
        }
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
