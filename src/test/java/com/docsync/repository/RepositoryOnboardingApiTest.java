package com.docsync.repository;

import com.docsync.github.GitHubAppClient;
import com.docsync.github.InstallationTokenService;
import com.docsync.github.dto.GitHubRepositorySummary;
import com.docsync.identity.github.GitHubOAuthProvider;
import com.docsync.identity.github.GitHubUser;
import com.docsync.repository.clone.GitOperationException;
import com.docsync.repository.clone.GitRepositoryCloner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepositoryOnboardingApiTest {

    private static final String PASSWORD = "a-strong-password-123";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GitHubOAuthProvider gitHubOAuthProvider;

    @MockBean
    private GitHubAppClient gitHubAppClient;

    @MockBean
    private InstallationTokenService tokenService;

    @MockBean
    private GitRepositoryCloner cloner;

    @Test
    void installationCreatedAutoLinksAndUpsertsGrantedRepositories() throws Exception {
        UserAccount owner = githubUser();
        String orgId = createOrganization(owner.token(), uniqueSlug()).get("id").asText();
        long installationId = uniqueId();

        sendInstallationWebhook("created", owner.githubId(), installationId, "delivery-" + UUID.randomUUID(),
                List.of(new RepoSpec(uniqueId(), "backend-api"), new RepoSpec(uniqueId(), "web-ui")));

        JsonNode connections = getJson("/api/v1/organizations/" + orgId + "/connections", owner.token());
        assertEquals(1, connections.size());
        assertEquals("ACTIVE", connections.get(0).get("status").asText());
        assertEquals(orgId, connections.get(0).get("organizationId").asText());
        assertEquals(installationId, connections.get(0).get("installationId").asLong());

        JsonNode repositories = getJson("/api/v1/organizations/" + orgId + "/repositories", owner.token());
        assertEquals(2, repositories.size());
        assertEquals("PENDING", repositories.get(0).get("status").asText());
        assertEquals("PENDING", repositories.get(1).get("status").asText());
        assertNotNull(findRepository(repositories, "backend-api"));
        assertNotNull(findRepository(repositories, "web-ui"));
    }

    @Test
    void duplicateDeliveryIsIdempotent() throws Exception {
        UserAccount owner = githubUser();
        String orgId = createOrganization(owner.token(), uniqueSlug()).get("id").asText();
        long installationId = uniqueId();
        String deliveryId = "duplicate-" + UUID.randomUUID();

        ObjectNode payload = installationPayload("created", installationId, owner.githubId(),
                List.of(new RepoSpec(uniqueId(), "backend-api")));

        sendWebhook("installation", deliveryId, payload);
        sendWebhook("installation", deliveryId, payload);

        assertEquals(1, getJson("/api/v1/organizations/" + orgId + "/connections", owner.token()).size());
        assertEquals(1, getJson("/api/v1/organizations/" + orgId + "/repositories", owner.token()).size());
    }

    @Test
    void invalidOrMissingSignatureIsRejected() throws Exception {
        ObjectNode payload = installationPayload("created", uniqueId(), 1L, List.of());
        byte[] body = objectMapper.writeValueAsBytes(payload);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "installation")
                        .header("X-GitHub-Delivery", "unsigned-" + UUID.randomUUID())
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_WEBHOOK_SIGNATURE"));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "installation")
                        .header("X-GitHub-Delivery", "bad-signature-" + UUID.randomUUID())
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_WEBHOOK_SIGNATURE"));
    }

    @Test
    void signedWebhookWithoutEventHeaderIsBadRequest() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(
                installationPayload("created", uniqueId(), 1L, List.of()));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Delivery", "missing-event-" + UUID.randomUUID())
                        .header("X-Hub-Signature-256", "sha256=" + hmac(body))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_WEBHOOK_INVALID"));
    }

    @Test
    void installationDeletedArchivesRepositories() throws Exception {
        UserAccount owner = githubUser();
        String orgId = createOrganization(owner.token(), uniqueSlug()).get("id").asText();
        long installationId = uniqueId();

        sendInstallationWebhook("created", owner.githubId(), installationId, "created-" + UUID.randomUUID(),
                List.of(new RepoSpec(uniqueId(), "backend-api"), new RepoSpec(uniqueId(), "web-ui")));
        sendInstallationWebhook("deleted", owner.githubId(), installationId, "deleted-" + UUID.randomUUID(),
                List.of());

        JsonNode connections = getJson("/api/v1/organizations/" + orgId + "/connections", owner.token());
        assertEquals("REVOKED", connections.get(0).get("status").asText());

        JsonNode archived = getJson(
                "/api/v1/organizations/" + orgId + "/repositories?status=ARCHIVED", owner.token());
        assertEquals(2, archived.size());
        assertEquals("ARCHIVED", archived.get(0).get("status").asText());
    }

    @Test
    void installationRepositoriesEventsAddAndRemoveRepositories() throws Exception {
        UserAccount owner = githubUser();
        String orgId = createOrganization(owner.token(), uniqueSlug()).get("id").asText();
        long installationId = uniqueId();
        long keptId = uniqueId();
        long removedId = uniqueId();

        sendInstallationWebhook("created", owner.githubId(), installationId, "created-" + UUID.randomUUID(),
                List.of(new RepoSpec(keptId, "backend-api")));

        ObjectNode added = objectMapper.createObjectNode();
        added.put("action", "added");
        added.putObject("installation").put("id", installationId);
        added.putObject("sender").put("id", owner.githubId());
        ArrayNode addedRepos = added.putArray("repositories_added");
        addRepo(addedRepos, new RepoSpec(removedId, "web-ui"));
        sendWebhook("installation_repositories", "added-" + UUID.randomUUID(), added);

        assertEquals(2, getJson("/api/v1/organizations/" + orgId + "/repositories", owner.token()).size());

        ObjectNode removed = objectMapper.createObjectNode();
        removed.put("action", "removed");
        removed.putObject("installation").put("id", installationId);
        removed.putObject("sender").put("id", owner.githubId());
        ArrayNode removedRepos = removed.putArray("repositories_removed");
        removedRepos.addObject().put("id", keptId);
        sendWebhook("installation_repositories", "removed-" + UUID.randomUUID(), removed);

        JsonNode repositories = getJson("/api/v1/organizations/" + orgId + "/repositories", owner.token());
        assertEquals("ARCHIVED", findRepository(repositories, "backend-api").get("status").asText());
        assertEquals("PENDING", findRepository(repositories, "web-ui").get("status").asText());
    }

    @Test
    void pendingInstallationCanBeClaimedByInstallerAndImportsRepositories() throws Exception {
        UserAccount installer = githubUser();
        long installationId = uniqueId();

        // No organization yet, so auto-linking cannot happen.
        sendInstallationWebhook("created", installer.githubId(), installationId, "pending-" + UUID.randomUUID(),
                List.of(new RepoSpec(uniqueId(), "backend-api")));

        JsonNode claimable = getJson("/api/v1/connections/claimable", installer.token());
        assertEquals(1, claimable.size());
        String connectionId = claimable.get(0).get("id").asText();

        String orgId = createOrganization(installer.token(), uniqueSlug()).get("id").asText();

        // An admin of another organization cannot claim an installation they did not make.
        UserAccount otherAdmin = githubUser();
        String otherOrgId = createOrganization(otherAdmin.token(), uniqueSlug()).get("id").asText();
        mockMvc.perform(post("/api/v1/organizations/{orgId}/connections/{connectionId}/claim",
                                otherOrgId, connectionId)
                        .header("Authorization", bearer(otherAdmin.token())))
                .andExpect(status().isForbidden());

        long importedId = uniqueId();
        when(tokenService.getToken(anyLong())).thenReturn("test-token");
        when(gitHubAppClient.listInstallationRepositories("test-token"))
                .thenReturn(List.of(new GitHubRepositorySummary(
                        importedId, "imported-repo", "acme-corp/imported-repo",
                        "https://github.com/acme-corp/imported-repo.git", "main", false, null)));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/connections/{connectionId}/claim",
                                orgId, connectionId)
                        .header("Authorization", bearer(installer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.organizationId").value(orgId));

        JsonNode repositories = getJson("/api/v1/organizations/" + orgId + "/repositories", installer.token());
        assertEquals(1, repositories.size());
        assertEquals("imported-repo", repositories.get(0).get("name").asText());
        assertEquals("PENDING", repositories.get(0).get("status").asText());

        UserAccount outsider = register();
        mockMvc.perform(post("/api/v1/organizations/{orgId}/connections/{connectionId}/claim",
                                orgId, connectionId)
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCanReadButNotConnectRepositories() throws Exception {
        UserAccount owner = githubUser();
        UserAccount member = register();
        String orgId = createOrganization(owner.token(), uniqueSlug()).get("id").asText();
        addMember(owner.token(), orgId, member.email(), "MEMBER");

        long installationId = uniqueId();
        sendInstallationWebhook("created", owner.githubId(), installationId, "member-" + UUID.randomUUID(),
                List.of(new RepoSpec(uniqueId(), "backend-api")));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories", orgId)
                        .header("Authorization", bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/repositories", orgId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "installationId", installationId,
                                "fullName", "acme-corp/backend-api"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void connectConfigureSyncAndDisconnectRepository() throws Exception {
        UserAccount owner = githubUser();
        String orgId = createOrganization(owner.token(), uniqueSlug()).get("id").asText();
        long installationId = uniqueId();
        long externalId = uniqueId();

        sendInstallationWebhook("created", owner.githubId(), installationId, "connect-" + UUID.randomUUID(),
                List.of());
        when(tokenService.getToken(installationId)).thenReturn("test-token");
        when(gitHubAppClient.getRepository("test-token", "acme-corp/backend-api"))
                .thenReturn(new GitHubRepositorySummary(
                        externalId, "backend-api", "acme-corp/backend-api",
                        "https://github.com/acme-corp/backend-api.git", "main", false, null));

        MvcResult connected = mockMvc.perform(post("/api/v1/organizations/{orgId}/repositories", orgId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "installationId", installationId,
                                "fullName", "acme-corp/backend-api",
                                "configuration", Map.of(
                                        "autoAnalyze", true,
                                        "languages", List.of("java", "kotlin"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.externalId").value(externalId))
                .andExpect(jsonPath("$.configuration.autoAnalyze").value(true))
                .andExpect(jsonPath("$.configuration.languages[0]").value("java"))
                .andReturn();
        String repositoryId = objectMapper.readTree(connected.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(patch("/api/v1/organizations/{orgId}/repositories/{repositoryId}/configuration",
                                orgId, repositoryId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "autoAnalyze", false,
                                "excludePaths", List.of("**/vendor/**")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuration.autoAnalyze").value(false))
                .andExpect(jsonPath("$.configuration.analyzePullRequests").value(true))
                .andExpect(jsonPath("$.configuration.languages[1]").value("kotlin"))
                .andExpect(jsonPath("$.configuration.excludePaths[0]").value("**/vendor/**"));

        String commitSha = "b".repeat(40);
        when(cloner.checkout(any(), eq("acme-corp/backend-api"), eq("main"), eq("test-token")))
                .thenReturn(new GitRepositoryCloner.CloneResult(
                        Path.of("target/test-workspaces", repositoryId), commitSha));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/repositories/{repositoryId}/sync",
                                orgId, repositoryId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.lastCommitSha").value(commitSha))
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty());

        UserAccount outsider = register();
        mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories/{repositoryId}", orgId, repositoryId)
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/organizations/{orgId}/repositories/{repositoryId}", orgId, repositoryId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories/{repositoryId}", orgId, repositoryId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/repositories/{repositoryId}/sync",
                                orgId, repositoryId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void syncFailureSetsErrorAndRetryRecovers() throws Exception {
        UserAccount owner = githubUser();
        String orgId = createOrganization(owner.token(), uniqueSlug()).get("id").asText();
        long installationId = uniqueId();
        long externalId = uniqueId();

        sendInstallationWebhook("created", owner.githubId(), installationId, "retry-" + UUID.randomUUID(),
                List.of(new RepoSpec(externalId, "backend-api")));

        String repositoryId = findRepository(
                getJson("/api/v1/organizations/" + orgId + "/repositories", owner.token()),
                "backend-api").get("id").asText();

        when(tokenService.getToken(installationId)).thenReturn("test-token");
        when(gitHubAppClient.getRepository("test-token", "acme-corp/backend-api"))
                .thenReturn(new GitHubRepositorySummary(
                        externalId, "backend-api", "acme-corp/backend-api",
                        "https://github.com/acme-corp/backend-api.git", "main", false, null));
        when(cloner.checkout(any(), anyString(), anyString(), anyString()))
                .thenThrow(new GitOperationException("Git operation failed: repository not found"));

        mockMvc.perform(post("/api/v1/organizations/{orgId}/repositories/{repositoryId}/sync",
                                orgId, repositoryId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("Git operation failed: repository not found"));

        String commitSha = "c".repeat(40);
        org.mockito.Mockito.doReturn(new GitRepositoryCloner.CloneResult(
                        Path.of("target/test-workspaces"), commitSha))
                .when(cloner).checkout(any(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/organizations/{orgId}/repositories/{repositoryId}/sync",
                                orgId, repositoryId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.lastCommitSha").value(commitSha))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    // ---------------------------------------------------------------- helpers

    private UserAccount githubUser() throws Exception {
        long githubId = uniqueId();
        String code = "code-" + UUID.randomUUID();
        String email = "github-" + UUID.randomUUID() + "@example.com";
        when(gitHubOAuthProvider.exchangeCode(code))
                .thenReturn(new GitHubUser(githubId, "octo-" + githubId, email, "Octo Installer", null));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new UserAccount(body.get("accessToken").asText(),
                body.get("user").get("id").asText(), githubId, email);
    }

    private UserAccount register() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", PASSWORD,
                                "displayName", "Test User"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new UserAccount(body.get("accessToken").asText(),
                body.get("user").get("id").asText(), null, email);
    }

    private JsonNode createOrganization(String token, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Acme Corp", "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void addMember(String ownerToken, String orgId, String email, String role) throws Exception {
        mockMvc.perform(post("/api/v1/organizations/{orgId}/members", orgId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "role", role))))
                .andExpect(status().isCreated());
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void sendInstallationWebhook(String action, long senderGithubId, long installationId,
                                         String deliveryId, List<RepoSpec> repositories) throws Exception {
        sendWebhook("installation", deliveryId,
                installationPayload(action, installationId, senderGithubId, repositories));
    }

    private ObjectNode installationPayload(String action, long installationId, long senderGithubId,
                                           List<RepoSpec> repositories) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("action", action);
        ObjectNode installation = payload.putObject("installation");
        installation.put("id", installationId);
        ObjectNode account = installation.putObject("account");
        account.put("login", "acme-corp");
        account.put("type", "Organization");
        ObjectNode sender = payload.putObject("sender");
        sender.put("id", senderGithubId);
        sender.put("login", "installer");
        ArrayNode repositoryArray = payload.putArray("repositories");
        repositories.forEach(repository -> addRepo(repositoryArray, repository));
        return payload;
    }

    private void addRepo(ArrayNode array, RepoSpec repository) {
        ObjectNode node = array.addObject();
        node.put("id", repository.externalId());
        node.put("name", repository.name());
        node.put("full_name", "acme-corp/" + repository.name());
        node.put("default_branch", "main");
    }

    private void sendWebhook(String eventType, String deliveryId, ObjectNode payload) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", eventType)
                        .header("X-GitHub-Delivery", deliveryId)
                        .header("X-Hub-Signature-256", "sha256=" + hmac(body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.eventId").value(deliveryId));
    }

    private String hmac(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private JsonNode findRepository(JsonNode repositories, String name) {
        for (JsonNode repository : repositories) {
            if (name.equals(repository.get("name").asText())) {
                return repository;
            }
        }
        return null;
    }

    private String uniqueSlug() {
        return "org-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long uniqueId() {
        return System.nanoTime() + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record UserAccount(String token, String userId, Long githubId, String email) {
    }

    private record RepoSpec(long externalId, String name) {
    }
}
