package com.docsync.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import com.docsync.identity.github.GitHubOAuthProvider;
import com.docsync.identity.github.GitHubUser;
import org.mockito.Mockito;
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
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookProcessingApiTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GitHubOAuthProvider gitHubOAuthProvider;

    @Test
    void pushToMonitoredBranchEnqueuesJob() throws Exception {
        TestFixtures fixtures = createRepository("push-monitored");

        ObjectNode push = pushPayload(fixtures.installationId(), fixtures.repositoryExternalId(),
                "refs/heads/main", "0000000000000000000000000000000000000000",
                "1111111111111111111111111111111111111111");
        sendWebhook("push", "push-1-" + UUID.randomUUID(), push)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories/{repoId}/jobs",
                        fixtures.organizationId(), fixtures.repositoryId())
                        .header("Authorization", "Bearer " + fixtures.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PUSH_ANALYSIS"))
                .andExpect(jsonPath("$[0].status").value("QUEUED"))
                .andExpect(jsonPath("$[0].triggerCommit").value("1111111111111111111111111111111111111111"));
    }

    @Test
    void pushToNonMonitoredBranchIsIgnored() throws Exception {
        TestFixtures fixtures = createRepository("push-ignored");

        ObjectNode push = pushPayload(fixtures.installationId(), fixtures.repositoryExternalId(),
                "refs/heads/feature/test", "0000000000000000000000000000000000000000",
                "2222222222222222222222222222222222222222");
        sendWebhook("push", "push-2-" + UUID.randomUUID(), push)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories/{repoId}/jobs",
                        fixtures.organizationId(), fixtures.repositoryId())
                        .header("Authorization", "Bearer " + fixtures.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateDeliveryDoesNotCreateDuplicateJob() throws Exception {
        TestFixtures fixtures = createRepository("push-duplicate");
        String deliveryId = "push-dup-" + UUID.randomUUID();

        ObjectNode push = pushPayload(fixtures.installationId(), fixtures.repositoryExternalId(),
                "refs/heads/main", "0000000000000000000000000000000000000000",
                "3333333333333333333333333333333333333333");

        sendWebhook("push", deliveryId, push).andExpect(status().isOk());
        sendWebhook("push", deliveryId, push).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories/{repoId}/jobs",
                        fixtures.organizationId(), fixtures.repositoryId())
                        .header("Authorization", "Bearer " + fixtures.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void pullRequestEventEnqueuesJobAndTracksPullRequest() throws Exception {
        TestFixtures fixtures = createRepository("pr-event");

        ObjectNode event = objectMapper.createObjectNode();
        event.put("action", "opened");
        ObjectNode installation = event.putObject("installation");
        installation.put("id", fixtures.installationId());
        ObjectNode repository = event.putObject("repository");
        repository.put("id", fixtures.repositoryExternalId());
        repository.put("full_name", "acme-corp/backend-api");
        ObjectNode pr = event.putObject("pull_request");
        pr.put("number", 42);
        pr.put("title", "Update API");
        pr.put("state", "open");
        ObjectNode user = pr.putObject("user");
        user.put("login", "octocat");
        ObjectNode head = pr.putObject("head");
        head.put("ref", "feature/api");
        head.put("sha", "4444444444444444444444444444444444444444");
        ObjectNode base = pr.putObject("base");
        base.put("ref", "main");
        base.put("sha", "5555555555555555555555555555555555555555");

        sendWebhook("pull_request", "pr-1-" + UUID.randomUUID(), event)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories/{repoId}/jobs",
                        fixtures.organizationId(), fixtures.repositoryId())
                        .header("Authorization", "Bearer " + fixtures.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PULL_REQUEST_ANALYSIS"))
                .andExpect(jsonPath("$[0].prNumber").value(42));
    }

    private org.springframework.test.web.servlet.ResultActions sendWebhook(String eventType, String deliveryId, ObjectNode payload)
            throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        return mockMvc.perform(post("/api/v1/webhooks/github")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Event", eventType)
                .header("X-GitHub-Delivery", deliveryId)
                .header("X-Hub-Signature-256", "sha256=" + hmac(body))
                .content(body));
    }

    private ObjectNode pushPayload(long installationId, long repositoryExternalId, String ref, String before, String after) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ref", ref);
        payload.put("before", before);
        payload.put("after", after);
        ObjectNode installation = payload.putObject("installation");
        installation.put("id", installationId);
        ObjectNode repository = payload.putObject("repository");
        repository.put("id", repositoryExternalId);
        repository.put("full_name", "acme-corp/backend-api");
        ArrayNode commits = payload.putArray("commits");
        ObjectNode commit = commits.addObject();
        commit.put("id", after);
        commit.putArray("added").add("src/New.java");
        commit.putArray("modified").add("src/Existing.java");
        commit.putArray("removed").add("src/Old.java");
        return payload;
    }

    private TestFixtures createRepository(String name) throws Exception {
        long githubId = Math.abs(System.nanoTime());
        String code = "code-" + UUID.randomUUID();
        String email = "github-" + UUID.randomUUID() + "@example.com";
        Mockito.when(gitHubOAuthProvider.exchangeCode(code))
                .thenReturn(new GitHubUser(githubId, "octo-" + githubId, email, "Octo Installer", null));

        MvcResult authResult = mockMvc.perform(post("/api/v1/auth/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("code", code))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(authResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        String slug = "org-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult orgResult = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Acme Corp", "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        String orgId = objectMapper.readTree(orgResult.getResponse().getContentAsString()).get("id").asText();

        long installationId = Math.abs(System.nanoTime()) + 1000;
        long externalId = Math.abs(System.nanoTime()) + 2000;

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("action", "created");
        ObjectNode installation = payload.putObject("installation");
        installation.put("id", installationId);
        ObjectNode account = installation.putObject("account");
        account.put("login", "acme-corp");
        account.put("type", "Organization");
        ObjectNode sender = payload.putObject("sender");
        sender.put("id", githubId);
        sender.put("login", "installer");
        ArrayNode repos = payload.putArray("repositories");
        ObjectNode repoNode = repos.addObject();
        repoNode.put("id", externalId);
        repoNode.put("name", "backend-api");
        repoNode.put("full_name", "acme-corp/backend-api");
        repoNode.put("default_branch", "main");

        byte[] body = objectMapper.writeValueAsBytes(payload);
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "installation")
                        .header("X-GitHub-Delivery", "install-" + UUID.randomUUID())
                        .header("X-Hub-Signature-256", "sha256=" + hmac(body))
                        .content(body))
                .andExpect(status().isOk());

        MvcResult reposResult = mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories", orgId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reposJson = objectMapper.readTree(reposResult.getResponse().getContentAsString());
        String repositoryId = null;
        for (JsonNode repo : reposJson) {
            if ("backend-api".equals(repo.get("name").asText())) {
                repositoryId = repo.get("id").asText();
                break;
            }
        }

        return new TestFixtures(token, installationId, externalId, UUID.fromString(orgId), UUID.fromString(repositoryId));
    }

    private String hmac(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private record TestFixtures(String token, long installationId, long repositoryExternalId, UUID organizationId, UUID repositoryId) {
    }
}
