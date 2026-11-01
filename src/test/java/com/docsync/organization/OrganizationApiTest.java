package com.docsync.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationApiTest {

    private static final String PASSWORD = "a-strong-password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createOrganizationMakesCreatorOwnerAndListsIt() throws Exception {
        UserAccount owner = register();
        JsonNode org = createOrganization(owner.token(), uniqueSlug());

        mockMvc.perform(get("/api/v1/organizations").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(org.get("id").asText()))
                .andExpect(jsonPath("$[0].currentUserRole").value("OWNER"))
                .andExpect(jsonPath("$[0].memberCount").value(1));
    }

    @Test
    void nonMemberCannotReadOrganization() throws Exception {
        UserAccount owner = register();
        UserAccount outsider = register();
        JsonNode org = createOrganization(owner.token(), uniqueSlug());

        mockMvc.perform(get("/api/v1/organizations/{id}", org.get("id").asText())
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void duplicateSlugIsRejected() throws Exception {
        UserAccount owner = register();
        String slug = uniqueSlug();
        createOrganization(owner.token(), slug);

        mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Other Org", "slug", slug))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void memberCanReadButNotUpdateUntilPromoted() throws Exception {
        UserAccount owner = register();
        UserAccount member = register();
        JsonNode org = createOrganization(owner.token(), uniqueSlug());
        String orgId = org.get("id").asText();

        MvcResult added = mockMvc.perform(post("/api/v1/organizations/{id}/members", orgId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", member.email(), "role", "MEMBER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andReturn();

        String membershipId = objectMapper.readTree(added.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/organizations/{id}", orgId)
                        .header("Authorization", bearer(member.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserRole").value("MEMBER"));

        mockMvc.perform(patch("/api/v1/organizations/{id}", orgId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Renamed"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/organizations/{id}/members/{memberId}", orgId, membershipId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(patch("/api/v1/organizations/{id}", orgId)
                        .header("Authorization", bearer(member.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Renamed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void removedMemberLosesAccess() throws Exception {
        UserAccount owner = register();
        UserAccount member = register();
        String orgId = createOrganization(owner.token(), uniqueSlug()).get("id").asText();

        MvcResult added = mockMvc.perform(post("/api/v1/organizations/{id}/members", orgId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", member.email(), "role", "MEMBER"))))
                .andExpect(status().isCreated())
                .andReturn();

        String membershipId = objectMapper.readTree(added.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(delete("/api/v1/organizations/{id}/members/{memberId}", orgId, membershipId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/organizations/{id}", orgId)
                        .header("Authorization", bearer(member.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastOwnerCannotBeRemoved() throws Exception {
        UserAccount owner = register();
        String orgId = createOrganization(owner.token(), uniqueSlug()).get("id").asText();

        MvcResult members = mockMvc.perform(get("/api/v1/organizations/{id}/members", orgId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andReturn();

        String membershipId = objectMapper.readTree(members.getResponse().getContentAsString())
                .get(0).get("id").asText();

        mockMvc.perform(delete("/api/v1/organizations/{id}/members/{memberId}", orgId, membershipId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
        return new UserAccount(email, token);
    }

    private JsonNode createOrganization(String token, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Acme Corp",
                                "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String uniqueSlug() {
        return "org-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record UserAccount(String email, String token) {
    }
}
