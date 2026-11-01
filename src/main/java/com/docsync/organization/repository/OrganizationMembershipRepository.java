package com.docsync.organization.repository;

import com.docsync.organization.entity.MemberRole;
import com.docsync.organization.entity.MembershipStatus;
import com.docsync.organization.entity.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    Optional<OrganizationMembership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    List<OrganizationMembership> findAllByUserId(UUID userId);

    List<OrganizationMembership> findAllByOrganizationIdOrderByJoinedAtAsc(UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, MembershipStatus status);

    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, MemberRole role, MembershipStatus status);
}
