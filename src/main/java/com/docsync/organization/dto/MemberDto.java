package com.docsync.organization.dto;

import com.docsync.organization.entity.MemberRole;
import com.docsync.organization.entity.MembershipStatus;
import com.docsync.organization.entity.OrganizationMembership;

import java.time.Instant;
import java.util.UUID;

public record MemberDto(
        UUID id,
        UUID userId,
        String email,
        String displayName,
        MemberRole role,
        MembershipStatus status,
        Instant joinedAt
) {

    public static MemberDto from(OrganizationMembership membership) {
        return new MemberDto(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getDisplayName(),
                membership.getRole(),
                membership.getStatus(),
                membership.getJoinedAt());
    }
}
