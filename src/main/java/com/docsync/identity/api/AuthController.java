package com.docsync.identity.api;

import com.docsync.identity.dto.AuthResponse;
import com.docsync.identity.dto.GitHubLoginRequest;
import com.docsync.identity.dto.LoginRequest;
import com.docsync.identity.dto.RegisterRequest;
import com.docsync.identity.service.IdentityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IdentityService identityService;

    public AuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(identityService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return identityService.login(request);
    }

    /**
     * Completes the GitHub OAuth flow after the browser receives the authorization code.
     */
    @PostMapping("/github")
    public AuthResponse loginWithGitHub(@Valid @RequestBody GitHubLoginRequest request) {
        return identityService.loginWithGitHub(request.code());
    }

    /**
     * Access tokens are stateless; the client discards its token. Present for API
     * compatibility and future refresh-token revocation.
     */
    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
