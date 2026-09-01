package com.realestate.controller;

import com.realestate.dto.user.UserDtos.MeResponse;
import com.realestate.dto.user.UserDtos.PublicProfileResponse;
import com.realestate.dto.user.UserDtos.UpdateMeRequest;
import com.realestate.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User projections and account self-service. Routes under /api/users.
 *
 * Credentials (register, login, OTP, password reset) stay under /auth and user
 * administration under /admin. What lives here is everything else about an
 * account: the public projection other people see, and the /me pair the owner
 * of the account reads and edits.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Public seller/agent profiles")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}/public")
    @Operation(summary = "Public profile for a seller/agent (no contact details)")
    public ResponseEntity<PublicProfileResponse> getPublicProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getPublicProfile(id));
    }

    // ── Account self-service ──────────────────────
    // Both resolve the account from the JWT principal, never from a path
    // variable: an id in the path would be an IDOR waiting to happen, and there
    // is no legitimate reason for one user to PATCH another's settings.

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's own account",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MeResponse> getMe(@AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(userService.getMe(currentUser.getUsername()));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the signed-in user's name, phone or notification settings",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MeResponse> updateMe(
            @Valid @RequestBody UpdateMeRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        return ResponseEntity.ok(userService.updateMe(currentUser.getUsername(), request));
    }
}
