package com.docsync.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record GitHubLoginRequest(
        @NotBlank String code
) {
}
