package com.docsync.repository.api;

import com.docsync.identity.security.CurrentUser;
import com.docsync.repository.dto.ConnectRepositoryRequest;
import com.docsync.repository.dto.RepositoryDto;
import com.docsync.repository.dto.UpdateRepositoryConfigurationRequest;
import com.docsync.repository.entity.RepositoryStatus;
import com.docsync.repository.service.RepositoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped repository onboarding API.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final CurrentUser currentUser;

    public RepositoryController(RepositoryService repositoryService, CurrentUser currentUser) {
        this.repositoryService = repositoryService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<RepositoryDto> list(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID organizationId,
                                    @RequestParam(required = false) RepositoryStatus status) {
        return repositoryService.listRepositories(currentUser.id(jwt), organizationId, status);
    }

    @PostMapping
    public ResponseEntity<RepositoryDto> connect(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID organizationId,
                                                 @Valid @RequestBody ConnectRepositoryRequest request) {
        RepositoryDto repository =
                repositoryService.connectRepository(currentUser.id(jwt), organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(repository);
    }

    @GetMapping("/{repositoryId}")
    public RepositoryDto get(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID organizationId,
                             @PathVariable UUID repositoryId) {
        return repositoryService.getRepository(currentUser.id(jwt), organizationId, repositoryId);
    }

    @PatchMapping("/{repositoryId}/configuration")
    public RepositoryDto updateConfiguration(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID organizationId,
                                             @PathVariable UUID repositoryId,
                                             @Valid @RequestBody UpdateRepositoryConfigurationRequest request) {
        return repositoryService.updateConfiguration(
                currentUser.id(jwt), organizationId, repositoryId, request);
    }

    @PostMapping("/{repositoryId}/sync")
    public RepositoryDto sync(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID organizationId,
                              @PathVariable UUID repositoryId) {
        return repositoryService.syncRepository(currentUser.id(jwt), organizationId, repositoryId);
    }

    @DeleteMapping("/{repositoryId}")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID organizationId,
                                           @PathVariable UUID repositoryId) {
        repositoryService.disconnectRepository(currentUser.id(jwt), organizationId, repositoryId);
        return ResponseEntity.noContent().build();
    }
}
