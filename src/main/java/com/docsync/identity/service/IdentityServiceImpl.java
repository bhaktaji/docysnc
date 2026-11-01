package com.docsync.identity.service;

import com.docsync.common.exception.AccessDeniedException;
import com.docsync.common.exception.DuplicateResourceException;
import com.docsync.common.exception.ResourceNotFoundException;
import com.docsync.identity.dto.AuthResponse;
import com.docsync.identity.dto.LoginRequest;
import com.docsync.identity.dto.RegisterRequest;
import com.docsync.identity.dto.UpdateProfileRequest;
import com.docsync.identity.dto.UserDto;
import com.docsync.identity.entity.User;
import com.docsync.identity.exception.InvalidCredentialsException;
import com.docsync.identity.github.GitHubOAuthProvider;
import com.docsync.identity.github.GitHubUser;
import com.docsync.identity.repository.UserRepository;
import com.docsync.identity.security.IssuedToken;
import com.docsync.identity.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class IdentityServiceImpl implements IdentityService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GitHubOAuthProvider gitHubOAuthProvider;

    public IdentityServiceImpl(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService,
                               GitHubOAuthProvider gitHubOAuthProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.gitHubOAuthProvider = gitHubOAuthProvider;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("User", email);
        }

        User user = new User(email, request.displayName().trim(),
                passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return issue(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        ensureActive(user);
        return issue(user);
    }

    @Override
    public AuthResponse loginWithGitHub(String authorizationCode) {
        GitHubUser githubUser = gitHubOAuthProvider.exchangeCode(authorizationCode);
        String email = normalizeEmail(githubUser.email());

        User user = userRepository.findByGithubId(githubUser.id())
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .orElseGet(() -> new User(email, githubUser.displayName(), null));

        ensureActive(user);

        if (user.getGithubId() != null && !user.getGithubId().equals(githubUser.id())) {
            throw new DuplicateResourceException("GitHub account", String.valueOf(githubUser.id()));
        }

        user.setGithubId(githubUser.id());
        user.setGithubLogin(githubUser.login());
        user.setEmail(email);
        if (githubUser.displayName() != null && !githubUser.displayName().isBlank()) {
            user.setDisplayName(githubUser.displayName());
        }
        if (githubUser.avatarUrl() != null) {
            user.setAvatarUrl(githubUser.avatarUrl());
        }

        userRepository.save(user);
        return issue(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getUser(UUID userId) {
        return UserDto.from(requireUser(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<UserDto> findByGithubId(Long githubId) {
        return userRepository.findByGithubId(githubId).map(UserDto::from);
    }

    @Override
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireUser(userId);

        if (request.displayName() != null) {
            if (request.displayName().isBlank()) {
                throw new com.docsync.common.exception.ValidationException("displayName", "must not be blank");
            }
            user.setDisplayName(request.displayName().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }

        return UserDto.from(userRepository.save(user));
    }

    private User requireUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        ensureActive(user);
        return user;
    }

    private void ensureActive(User user) {
        if (!user.isActive()) {
            throw new AccessDeniedException("User account is not active");
        }
    }

    private AuthResponse issue(User user) {
        IssuedToken token = jwtService.issue(user);
        return AuthResponse.bearer(token.token(), token.expiresInSeconds(), UserDto.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
