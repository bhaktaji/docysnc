package com.docsync.docgen.api;

import com.docsync.docgen.dto.GeneratedChangeDto;
import com.docsync.docgen.entity.GeneratedDocumentationChange;
import com.docsync.docgen.service.DocumentationGenerationService;
import com.docsync.identity.security.CurrentUser;
import com.docsync.organization.service.OrganizationAuthorizationService;
import com.docsync.repository.entity.Repository;
import com.docsync.repository.service.RepositoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/repositories/{repositoryId}")
public class DocumentationGenerationController {

    private final DocumentationGenerationService generationService;
    private final RepositoryService repositoryService;
    private final OrganizationAuthorizationService authorizationService;
    private final CurrentUser currentUser;

    public DocumentationGenerationController(DocumentationGenerationService generationService,
                                            RepositoryService repositoryService,
                                            OrganizationAuthorizationService authorizationService,
                                            CurrentUser currentUser) {
        this.generationService = generationService;
        this.repositoryService = repositoryService;
        this.authorizationService = authorizationService;
        this.currentUser = currentUser;
    }

    @PostMapping("/documentation/generate")
    public ResponseEntity<List<GeneratedChangeDto>> generate(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable UUID organizationId,
                                                            @PathVariable UUID repositoryId,
                                                            @RequestParam(required = false) UUID analysisId) {
        authorizationService.requireRole(currentUser.id(jwt), organizationId,
                com.docsync.organization.entity.MemberRole.MEMBER);
        Repository repository = repositoryService.findRepositoryById(repositoryId)
                .orElseThrow(() -> new com.docsync.common.exception.ResourceNotFoundException("Repository", repositoryId.toString()));
        if (!repository.getOrganizationId().equals(organizationId)) {
            throw new com.docsync.common.exception.ResourceNotFoundException("Repository", repositoryId.toString());
        }
        var result = generationService.generateUpdates(repositoryId, analysisId);
        return ResponseEntity.ok(result.changes().stream().map(GeneratedChangeDto::from).toList());
    }

    @GetMapping("/documentation/changes")
    public List<GeneratedChangeDto> listChanges(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID organizationId,
                                               @PathVariable UUID repositoryId) {
        authorizationService.requireMembership(currentUser.id(jwt), organizationId);
        return generationService.getChangesForRepository(repositoryId).stream()
                .map(GeneratedChangeDto::from).toList();
    }

    @GetMapping("/documentation/changes/{changeId}")
    public GeneratedChangeDto getChange(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID organizationId,
                                       @PathVariable UUID repositoryId,
                                       @PathVariable UUID changeId) {
        authorizationService.requireMembership(currentUser.id(jwt), organizationId);
        return GeneratedChangeDto.from(generationService.getChange(changeId));
    }

    @PostMapping("/documentation/changes/{changeId}/approve")
    public ResponseEntity<Void> approve(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID organizationId,
                                       @PathVariable UUID repositoryId,
                                       @PathVariable UUID changeId) {
        authorizationService.requireRole(currentUser.id(jwt), organizationId,
                com.docsync.organization.entity.MemberRole.MEMBER);
        generationService.approveChange(changeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documentation/changes/{changeId}/reject")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID organizationId,
                                      @PathVariable UUID repositoryId,
                                      @PathVariable UUID changeId) {
        authorizationService.requireRole(currentUser.id(jwt), organizationId,
                com.docsync.organization.entity.MemberRole.MEMBER);
        generationService.rejectChange(changeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documentation/changes/{changeId}/regenerate")
    public GeneratedChangeDto regenerate(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID organizationId,
                                        @PathVariable UUID repositoryId,
                                        @PathVariable UUID changeId) {
        authorizationService.requireRole(currentUser.id(jwt), organizationId,
                com.docsync.organization.entity.MemberRole.MEMBER);
        return GeneratedChangeDto.from(generationService.regenerateChange(changeId));
    }
}
