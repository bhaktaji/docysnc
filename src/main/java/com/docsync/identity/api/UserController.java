package com.docsync.identity.api;

import com.docsync.identity.dto.UpdateProfileRequest;
import com.docsync.identity.dto.UserDto;
import com.docsync.identity.security.CurrentUser;
import com.docsync.identity.service.IdentityService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final IdentityService identityService;
    private final CurrentUser currentUser;

    public UserController(IdentityService identityService, CurrentUser currentUser) {
        this.identityService = identityService;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return identityService.getUser(currentUser.id(jwt));
    }

    @PatchMapping("/me")
    public UserDto updateMe(@AuthenticationPrincipal Jwt jwt,
                            @Valid @RequestBody UpdateProfileRequest request) {
        return identityService.updateProfile(currentUser.id(jwt), request);
    }
}
