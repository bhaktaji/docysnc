package com.docsync.identity;

import com.docsync.identity.github.GitHubOAuthProvider;
import com.docsync.identity.github.GitHubUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GitHubAuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GitHubOAuthProvider gitHubOAuthProvider;

    @Test
    void githubLoginCreatesUserAndIsIdempotent() throws Exception {
        long githubId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        GitHubUser profile = new GitHubUser(githubId, "octocat", uniqueEmail(),
                "Octo Cat", "https://avatars.example.com/octo.png");
        when(gitHubOAuthProvider.exchangeCode(anyString())).thenReturn(profile);

        MvcResult first = mockMvc.perform(post("/api/v1/auth/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "first-code"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.githubLogin").value("octocat"))
                .andExpect(jsonPath("$.user.displayName").value("Octo Cat"))
                .andReturn();

        String token = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubLogin").value("octocat"));

        // A second authorization with the same GitHub identity must reuse the user.
        mockMvc.perform(post("/api/v1/auth/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "second-code"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(
                        objectMapper.readTree(first.getResponse().getContentAsString())
                                .get("user").get("id").asText()));
    }

    private String uniqueEmail() {
        return "github-" + UUID.randomUUID() + "@example.com";
    }
}
