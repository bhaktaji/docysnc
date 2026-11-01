package com.docsync.connection.api;

import com.docsync.connection.dto.RepositoryConnectionDto;
import com.docsync.connection.service.RepositoryConnectionService;
import com.docsync.identity.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * GitHub App installation connections.
 */
@RestController
@RequestMapping("/api/v1")
public class RepositoryConnectionController {

    private final RepositoryConnectionService connectionService;
    private final CurrentUser currentUser;

    public RepositoryConnectionController(RepositoryConnectionService connectionService,
                                          CurrentUser currentUser) {
        this.connectionService = connectionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/organizations/{organizationId}/connections")
    public List<RepositoryConnectionDto> list(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID organizationId) {
        return connectionService.listConnections(currentUser.id(jwt), organizationId);
    }

    @GetMapping("/connections/claimable")
    public List<RepositoryConnectionDto> claimable(@AuthenticationPrincipal Jwt jwt) {
        return connectionService.listClaimableConnections(currentUser.id(jwt));
    }

    @PostMapping("/organizations/{organizationId}/connections/{connectionId}/claim")
    public RepositoryConnectionDto claim(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID organizationId,
                                         @PathVariable UUID connectionId) {
        return connectionService.claimConnection(currentUser.id(jwt), organizationId, connectionId);
    }

    @DeleteMapping("/organizations/{organizationId}/connections/{connectionId}")
    public RepositoryConnectionDto revoke(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable UUID organizationId,
                                          @PathVariable UUID connectionId) {
        return connectionService.revokeConnection(currentUser.id(jwt), organizationId, connectionId);
    }
}
