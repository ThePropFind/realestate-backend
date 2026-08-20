package com.realestate.service;

import com.realestate.dto.user.UserDtos.PublicProfileResponse;
import com.realestate.entity.Property;
import com.realestate.entity.User;
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
}
