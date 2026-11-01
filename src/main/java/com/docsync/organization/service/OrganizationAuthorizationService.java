package com.docsync.organization.service;

import com.docsync.common.exception.AccessDeniedException;
import com.docsync.organization.entity.MemberRole;
import com.docsync.organization.entity.OrganizationMembership;
import com.docsync.organization.repository.OrganizationMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Central tenant authorization for organization-scoped resources.
 *
 * <p>Other modules must use this service instead of reading membership data directly.
 * A missing, inactive, or foreign membership is reported as access denied so that the
 * existence of another tenant's organization is not disclosed.</p>
 */
@Service
@Transactional(readOnly = true)
public class OrganizationAuthorizationService {

    private final OrganizationMembershipRepository membershipRepository;

    public OrganizationAuthorizationService(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public OrganizationMembership requireMembership(UUID actorId, UUID organizationId) {
        OrganizationMembership membership = membershipRepository
                .findByUserIdAndOrganizationId(actorId, organizationId)
                .orElseThrow(() -> new AccessDeniedException("Organization", organizationId.toString()));

        if (!membership.isActive()) {
            throw new AccessDeniedException("Organization", organizationId.toString());
        }
        return membership;
    }

    public OrganizationMembership requireRole(UUID actorId, UUID organizationId, MemberRole minimum) {
        OrganizationMembership membership = requireMembership(actorId, organizationId);
        if (!membership.getRole().atLeast(minimum)) {
            throw new AccessDeniedException("Insufficient role for organization: " + organizationId);
        }
        return membership;
    }

    /**
     * Organizations where the user is an active ADMIN or OWNER. Used to auto-link a
     * GitHub App installation to a single unambiguous organization.
     */
    public List<UUID> getAdminOrganizationIds(UUID userId) {
        return membershipRepository.findAllByUserId(userId).stream()
                .filter(OrganizationMembership::isActive)
                .filter(membership -> membership.getRole().atLeast(MemberRole.ADMIN))
                .map(membership -> membership.getOrganization().getId())
                .toList();
    }
}
