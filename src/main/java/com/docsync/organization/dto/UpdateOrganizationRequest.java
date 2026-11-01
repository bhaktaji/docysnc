package com.docsync.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Email @Size(max = 255) String billingEmail
) {
}
