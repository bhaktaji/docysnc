package com.docsync.organization.service;

import com.docsync.common.exception.AccessDeniedException;
import com.docsync.common.exception.DuplicateResourceException;
import com.docsync.common.exception.ResourceNotFoundException;
import com.docsync.common.exception.ValidationException;
import com.docsync.identity.entity.User;
import com.docsync.identity.repository.UserRepository;
import com.docsync.organization.dto.AddMemberRequest;
import com.docsync.organization.dto.CreateOrganizationRequest;
import com.docsync.organization.dto.MemberDto;
import com.docsync.organization.dto.OrganizationDto;
import com.docsync.organization.dto.UpdateMemberRoleRequest;
import com.docsync.organization.dto.UpdateOrganizationRequest;
import com.docsync.organization.entity.MemberRole;
import com.docsync.organization.entity.MembershipStatus;
import com.docsync.organization.entity.Organization;
import com.docsync.organization.entity.OrganizationMembership;
import com.docsync.organization.repository.OrganizationMembershipRepository;
import com.docsync.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Organization and membership management with tenant-scoped authorization.
 * Every operation requires an explicit actor and verifies active membership.
 */
@Service
@Transactional
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final OrganizationAuthorizationService authorizationService;

    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
                                   OrganizationMembershipRepository membershipRepository,
                                   UserRepository userRepository,
                                   OrganizationAuthorizationService authorizationService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public OrganizationDto createOrganization(UUID actorId, CreateOrganizationRequest request) {
        User actor = requireUser(actorId);
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);

        if (organizationRepository.existsBySlugIgnoreCase(slug)) {
            throw new DuplicateResourceException("Organization", slug);
        }

        String description = request.description() == null ? null : request.description().trim();
        Organization organization = new Organization(request.name().trim(), slug, description);
        organizationRepository.save(organization);

        OrganizationMembership membership =
                new OrganizationMembership(actor, organization, MemberRole.OWNER);
        membershipRepository.save(membership);

        return OrganizationDto.from(organization, MemberRole.OWNER, 1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationDto> getUserOrganizations(UUID actorId) {
        return membershipRepository.findAllByUserId(actorId).stream()
                .filter(OrganizationMembership::isActive)
                .map(membership -> OrganizationDto.from(
                        membership.getOrganization(),
                        membership.getRole(),
                        membershipRepository.countByOrganizationIdAndStatus(
                                membership.getOrganization().getId(), MembershipStatus.ACTIVE)))
                .sorted(Comparator.comparing(OrganizationDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationDto getOrganization(UUID actorId, UUID organizationId) {
        OrganizationMembership membership = requireMembership(actorId, organizationId);
        return toDto(membership.getOrganization(), membership.getRole());
    }

    @Override
    public OrganizationDto updateOrganization(UUID actorId, UUID organizationId,
                                              UpdateOrganizationRequest request) {
        OrganizationMembership membership = requireRole(actorId, organizationId, MemberRole.ADMIN);
        Organization organization = membership.getOrganization();

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ValidationException("name", "must not be blank");
            }
            organization.setName(request.name().trim());
        }
        if (request.description() != null) {
            organization.setDescription(request.description().isBlank() ? null : request.description().trim());
        }
        if (request.billingEmail() != null) {
            organization.setBillingEmail(
                    request.billingEmail().isBlank() ? null : request.billingEmail().trim().toLowerCase(Locale.ROOT));
        }

        organizationRepository.save(organization);
        return toDto(organization, membership.getRole());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberDto> getMembers(UUID actorId, UUID organizationId) {
        requireMembership(actorId, organizationId);
        return membershipRepository.findAllByOrganizationIdOrderByJoinedAtAsc(organizationId).stream()
                .map(MemberDto::from)
                .toList();
    }

    @Override
    public MemberDto addMember(UUID actorId, UUID organizationId, AddMemberRequest request) {
        OrganizationMembership actorMembership = requireRole(actorId, organizationId, MemberRole.ADMIN);
        assertCanAssignRole(actorMembership.getRole(), request.role());

        User target = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ValidationException("email",
                        "no DocSync user exists with this email address"));

        OrganizationMembership existing = membershipRepository
                .findByUserIdAndOrganizationId(target.getId(), organizationId)
                .orElse(null);

        if (existing != null) {
            if (existing.isActive()) {
                throw new DuplicateResourceException("Member", target.getEmail());
            }
            existing.setStatus(MembershipStatus.ACTIVE);
            existing.setRole(request.role());
            return MemberDto.from(membershipRepository.save(existing));
        }

        OrganizationMembership membership = new OrganizationMembership(
                target, actorMembership.getOrganization(), request.role());
        return MemberDto.from(membershipRepository.save(membership));
    }

    @Override
    public MemberDto updateMemberRole(UUID actorId, UUID organizationId, UUID membershipId,
                                      UpdateMemberRoleRequest request) {
        OrganizationMembership actorMembership = requireRole(actorId, organizationId, MemberRole.ADMIN);
        OrganizationMembership target = requireMembershipInOrganization(organizationId, membershipId);
        assertCanAssignRole(actorMembership.getRole(), request.role());

        if (target.getRole() == MemberRole.OWNER && request.role() != MemberRole.OWNER) {
            ensureNotLastOwner(organizationId);
        }

        target.setRole(request.role());
        return MemberDto.from(membershipRepository.save(target));
    }

    @Override
    public void removeMember(UUID actorId, UUID organizationId, UUID membershipId) {
        requireRole(actorId, organizationId, MemberRole.ADMIN);
        OrganizationMembership target = requireMembershipInOrganization(organizationId, membershipId);

        if (target.getRole() == MemberRole.OWNER) {
            ensureNotLastOwner(organizationId);
        }

        membershipRepository.delete(target);
    }

    private OrganizationMembership requireMembership(UUID actorId, UUID organizationId) {
        return authorizationService.requireMembership(actorId, organizationId);
    }

    private OrganizationMembership requireRole(UUID actorId, UUID organizationId, MemberRole minimum) {
        return authorizationService.requireRole(actorId, organizationId, minimum);
    }

    private OrganizationMembership requireMembershipInOrganization(UUID organizationId, UUID membershipId) {
        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", membershipId.toString()));
        if (!membership.getOrganization().getId().equals(organizationId)) {
            throw new ResourceNotFoundException("Member", membershipId.toString());
        }
        return membership;
    }

    private void assertCanAssignRole(MemberRole actorRole, MemberRole requested) {
        if (requested.isHigherThan(actorRole)) {
            throw new AccessDeniedException("Cannot assign a role higher than your own");
        }
    }

    private void ensureNotLastOwner(UUID organizationId) {
        long owners = membershipRepository.countByOrganizationIdAndRoleAndStatus(
                organizationId, MemberRole.OWNER, MembershipStatus.ACTIVE);
        if (owners <= 1) {
            throw new ValidationException("role", "cannot remove or demote the last organization owner");
        }
    }

    private OrganizationDto toDto(Organization organization, MemberRole role) {
        long members = membershipRepository.countByOrganizationIdAndStatus(
                organization.getId(), MembershipStatus.ACTIVE);
        return OrganizationDto.from(organization, role, members);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }
}
