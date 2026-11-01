package com.docsync.organization.dto;

import com.docsync.organization.entity.MemberRole;
import com.docsync.organization.entity.Organization;
import com.docsync.organization.entity.OrganizationPlan;

import java.time.Instant;
import java.util.UUID;

public record OrganizationDto(
        UUID id,
        String name,
        String slug,
        String description,
        OrganizationPlan plan,
        String billingEmail,
        MemberRole currentUserRole,
        long memberCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrganizationDto from(Organization org, MemberRole role, long memberCount) {
        return new OrganizationDto(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getDescription(),
                org.getPlan(),
                org.getBillingEmail(),
                role,
                memberCount,
                org.getCreatedAt(),
                org.getUpdatedAt());
    }
}
