package com.docsync.identity.service;

import com.docsync.identity.dto.AuthResponse;
import com.docsync.identity.dto.LoginRequest;
import com.docsync.identity.dto.RegisterRequest;
import com.docsync.identity.dto.UpdateProfileRequest;
import com.docsync.identity.dto.UserDto;

import java.util.Optional;
import java.util.UUID;

public interface IdentityService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse loginWithGitHub(String authorizationCode);

    UserDto getUser(UUID userId);

    Optional<UserDto> findByGithubId(Long githubId);

    UserDto updateProfile(UUID userId, UpdateProfileRequest request);
}
