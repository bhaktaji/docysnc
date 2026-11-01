package com.docsync.organization.service;

import com.docsync.organization.dto.AddMemberRequest;
import com.docsync.organization.dto.CreateOrganizationRequest;
import com.docsync.organization.dto.MemberDto;
import com.docsync.organization.dto.OrganizationDto;
import com.docsync.organization.dto.UpdateMemberRoleRequest;
import com.docsync.organization.dto.UpdateOrganizationRequest;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {

    OrganizationDto createOrganization(UUID actorId, CreateOrganizationRequest request);

    List<OrganizationDto> getUserOrganizations(UUID actorId);

    OrganizationDto getOrganization(UUID actorId, UUID organizationId);

    OrganizationDto updateOrganization(UUID actorId, UUID organizationId, UpdateOrganizationRequest request);

    List<MemberDto> getMembers(UUID actorId, UUID organizationId);

    MemberDto addMember(UUID actorId, UUID organizationId, AddMemberRequest request);

    MemberDto updateMemberRole(UUID actorId, UUID organizationId, UUID membershipId,
                               UpdateMemberRoleRequest request);

    void removeMember(UUID actorId, UUID organizationId, UUID membershipId);
}
