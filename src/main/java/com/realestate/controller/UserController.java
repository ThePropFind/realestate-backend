package com.realestate.controller;

import com.realestate.dto.user.UserDtos.PublicProfileResponse;
import com.realestate.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public user projections. Routes under /api/users.
 *
 * Only the public profile lives here — account self-service is under /auth and
 * user administration is under /admin.
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
}
