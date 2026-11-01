package com.docsync.identity.dto;

import com.docsync.identity.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        Long githubId,
        String githubLogin,
        Instant createdAt
) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getGithubId(),
                user.getGithubLogin(),
                user.getCreatedAt());
    }
}
