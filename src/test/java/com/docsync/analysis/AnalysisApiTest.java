package com.docsync.analysis;

import com.docsync.identity.github.GitHubOAuthProvider;
import com.docsync.identity.github.GitHubUser;
import com.docsync.github.GitHubAppClient;
import com.docsync.github.InstallationTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisApiTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final String JAVA_SOURCE = """
            package com.example;

            /**
             * User service.
             */
            public class UserService {
                /**
                 * Creates a user.
                 */
                public String createUser(String name) {
                    return name;
                }
            }
            """;

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

    @Test
    void fullAnalysisParsesJavaAndDocumentation() throws Exception {
        TestFixtures fixtures = createRepository();

        Mockito.when(tokenService.getToken(anyLong())).thenReturn("test-token");
        Mockito.when(gitHubAppClient.listRepositoryTree(eq("test-token"), eq("acme-corp/backend-api"), eq("main")))
                .thenReturn(List.of("src/main/java/com/example/UserService.java", "README.md"));
        Mockito.when(gitHubAppClient.getFileContent(eq("test-token"), eq("acme-corp/backend-api"),
                eq("src/main/java/com/example/UserService.java"), eq("main")))
                .thenReturn(JAVA_SOURCE);
        Mockito.when(gitHubAppClient.getFileContent(eq("test-token"), eq("acme-corp/backend-api"),
                eq("README.md"), eq("main")))
                .thenReturn("# README\n\nUses `UserService.createUser()`.\n");

        mockMvc.perform(post("/api/v1/organizations/{orgId}/repositories/{repoId}/analysis",
                        fixtures.organizationId(), fixtures.repositoryId())
                        .header("Authorization", "Bearer " + fixtures.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.stats.entitiesFound").value(2))
                .andExpect(jsonPath("$.stats.documentationPages").value(1));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories/{repoId}/findings",
                        fixtures.organizationId(), fixtures.repositoryId())
                        .header("Authorization", "Bearer " + fixtures.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/organizations/{orgId}/repositories/{repoId}/documentation",
                        fixtures.organizationId(), fixtures.repositoryId())
                        .header("Authorization", "Bearer " + fixtures.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].path").value("README.md"));
    }

    private TestFixtures createRepository() throws Exception {
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

        return new TestFixtures(token, UUID.fromString(orgId), UUID.fromString(repositoryId));
    }

    private String hmac(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private record TestFixtures(String token, UUID organizationId, UUID repositoryId) {
    }
}
