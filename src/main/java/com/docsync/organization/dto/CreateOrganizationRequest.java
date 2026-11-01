package com.docsync.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank
        @Size(min = 2, max = 100)
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$",
                message = "must contain only lowercase letters, digits, and hyphens")
        String slug,
        @Size(max = 500) String description
) {
}
