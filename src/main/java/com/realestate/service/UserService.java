package com.realestate.service;

import com.realestate.dto.user.UserDtos.MeResponse;
import com.realestate.dto.user.UserDtos.PublicProfileResponse;
import com.realestate.dto.user.UserDtos.UpdateMeRequest;
import com.realestate.entity.Property;
import com.realestate.entity.User;
import com.realestate.exception.BadRequestException;
import com.realestate.exception.ConflictException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.repository.PropertyRepository;
import com.realestate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only user projections for other users to see.
 *
 * Separate from AuthService on purpose: AuthService owns credentials, OTP and
 * tokens, and nothing here should ever be one refactor away from that surface.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository     userRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyService    propertyService;

    /** Most recent listings shown inline on the profile; the count is the true total. */
    private static final int MAX_PROFILE_LISTINGS = 12;

    /**
     * Public profile for a seller/agent, reachable without a token.
     *
     * 404s for an unknown id and for a deactivated (banned) account alike — a banned
     * seller's page must not stay live, and answering differently for "no such user"
     * and "banned user" would turn the endpoint into a ban oracle.
     */
    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .filter(User::isActive)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        var listings = propertyRepository
            .findByOwnerIdAndStatusOrderByCreatedAtDesc(
                userId, Property.ListingStatus.ACTIVE, PageRequest.of(0, MAX_PROFILE_LISTINGS))
            .stream()
            .map(propertyService::toCardResponse)
            .toList();

        return PublicProfileResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .role(user.getRole().name())
            .profilePhotoUrl(user.getProfilePhotoUrl())
            .isEmailVerified(user.isVerified())
            .memberSince(user.getCreatedAt())
            .activeListingCount(
                propertyRepository.countByOwnerIdAndStatus(userId, Property.ListingStatus.ACTIVE))
            .listings(listings)
            .build();
    }

    // ─────────────────────────────────────────────
    // ACCOUNT SELF-SERVICE (Settings screen)
    // ─────────────────────────────────────────────

    /** The signed-in user's own account. Resolved from the token, never from a path id. */
    @Transactional(readOnly = true)
    public MeResponse getMe(String email) {
        return toMeResponse(requireUser(email));
    }

    /**
     * Partial update of the signed-in account.
     *
     * A null field means "leave it alone" — this is a PATCH, and treating a
     * missing key as "set to null" is the exact bug that made the web edit form
     * wipe columns (regression #88). Only three things are writable here; email,
     * role and password are deliberately not among them.
     */
    @Transactional
    public MeResponse updateMe(String email, UpdateMeRequest req) {
        User user = requireUser(email);

        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.isEmpty()) throw new BadRequestException("Name cannot be blank");
            user.setName(name);
        }

        if (req.getPhone() != null) {
            String phone = req.getPhone().trim();
            if (phone.isEmpty()) {
                // Explicit clear. Phone is optional and unverified by design
                // (email-OTP-only launch), so removing it is allowed.
                user.setPhone(null);
            } else {
                // The column is unique and a phone is a login identifier, so a
                // collision has to be a 409 rather than a 500 from the constraint.
                userRepository.findByPhone(phone)
                    .filter(other -> !other.getId().equals(user.getId()))
                    .ifPresent(other -> {
                        throw new ConflictException("That mobile number is already registered");
                    });
                user.setPhone(phone);
            }
        }

        if (req.getNotifyEmailInquiries() != null) {
            user.setNotifyEmailInquiries(req.getNotifyEmailInquiries());
        }

        return toMeResponse(userRepository.save(user));
    }

    /**
     * The principal's row. A token that survives the account being deleted or
     * banned must not resolve — 404 rather than a NullPointerException later.
     */
    private User requireUser(String email) {
        return userRepository.findByEmail(email)
            .filter(User::isActive)
            .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    private MeResponse toMeResponse(User user) {
        return MeResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .role(user.getRole().name())
            .profilePhotoUrl(user.getProfilePhotoUrl())
            .isEmailVerified(user.isVerified())
            .memberSince(user.getCreatedAt())
            .notifyEmailInquiries(user.isNotifyEmailInquiries())
            .build();
    }
}
