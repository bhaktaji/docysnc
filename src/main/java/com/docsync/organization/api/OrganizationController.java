package com.docsync.organization.api;

import com.docsync.identity.security.CurrentUser;
import com.docsync.organization.dto.AddMemberRequest;
import com.docsync.organization.dto.CreateOrganizationRequest;
import com.docsync.organization.dto.MemberDto;
import com.docsync.organization.dto.OrganizationDto;
import com.docsync.organization.dto.UpdateMemberRoleRequest;
import com.docsync.organization.dto.UpdateOrganizationRequest;
import com.docsync.organization.service.OrganizationService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final CurrentUser currentUser;

    public OrganizationController(OrganizationService organizationService, CurrentUser currentUser) {
        this.organizationService = organizationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<OrganizationDto> list(@AuthenticationPrincipal Jwt jwt) {
        return organizationService.getUserOrganizations(currentUser.id(jwt));
    }

    @PostMapping
    public ResponseEntity<OrganizationDto> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationDto created = organizationService.createOrganization(currentUser.id(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public OrganizationDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return organizationService.getOrganization(currentUser.id(jwt), id);
    }

    @PatchMapping("/{id}")
    public OrganizationDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.updateOrganization(currentUser.id(jwt), id, request);
    }

    @GetMapping("/{id}/members")
    public List<MemberDto> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return organizationService.getMembers(currentUser.id(jwt), id);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<MemberDto> addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @Valid @RequestBody AddMemberRequest request) {
        MemberDto member = organizationService.addMember(currentUser.id(jwt), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PatchMapping("/{id}/members/{membershipId}")
    public MemberDto updateMemberRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @PathVariable UUID membershipId,
                                      @Valid @RequestBody UpdateMemberRoleRequest request) {
        return organizationService.updateMemberRole(
                currentUser.id(jwt), id, membershipId, request);
    }

    @DeleteMapping("/{id}/members/{membershipId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                             @PathVariable UUID membershipId) {
        organizationService.removeMember(currentUser.id(jwt), id, membershipId);
        return ResponseEntity.noContent().build();
    }
}
