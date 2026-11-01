package com.docsync.organization.dto;

import com.docsync.organization.entity.MemberRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull MemberRole role
) {
}
