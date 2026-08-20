package com.realestate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realestate.dto.user.UserDtos.PublicProfileResponse;
import com.realestate.entity.City;
import com.realestate.entity.Locality;
import com.realestate.entity.Property;
import com.realestate.entity.User;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.repository.PropertyRepository;
import com.realestate.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the public owner profile (B5).
 *
 * The load-bearing assertion is the negative one: this endpoint is reachable with
 * no token at all, so it must never carry a phone number or an email address. The
 * owner's phone already reaches buyers through the detail page's Call/WhatsApp CTAs;
 * a profile that repeated it would be a second, unrate-limited way to harvest
 * contact details for every seller by walking user ids.
 */
@ExtendWith(MockitoExtension.class)
class UserPublicProfileTest {

    @Mock private UserRepository     userRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private PropertyService    propertyService;

    @InjectMocks private UserService userService;

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final LocalDateTime JOINED = LocalDateTime.of(2022, 3, 9, 11, 15);

    private final UUID userId = UUID.randomUUID();

    private User seller(boolean active) {
        return User.builder()
            .id(userId)
            .name("Anitha R")
            .email("anitha@propfind.in")
            .phone("9876543210")
            .role(User.Role.SELLER)
            .verified(true)
            .active(active)
            .createdAt(JOINED)
            .build();
    }

    private Property activeListing() {
        City city = City.builder().id(UUID.randomUUID()).name("Coimbatore").slug("coimbatore").build();
        Locality locality = Locality.builder()
            .id(UUID.randomUUID()).name("Gandhipuram").slug("gandhipuram").city(city).build();

        return Property.builder()
            .id(UUID.randomUUID())
            .owner(seller(true))
            .locality(locality)
            .title("3BHK Individual Villa")
            .listingType(Property.ListingType.SALE)
            .propertyType(Property.PropertyType.VILLA)
            .status(Property.ListingStatus.ACTIVE)
            .price(new BigDecimal("9000000"))
            .priceUnit(Property.PriceUnit.TOTAL)
            .furnishing(Property.FurnishingStatus.SEMI_FURNISHED)
            .areaSqft(new BigDecimal("1938"))
            .images(List.of())
            .amenities(Set.of())
            .build();
    }

    // ── Contact details must not appear ──────────────────────────

    @Test
    void profileSerializesWithNoPhoneAndNoEmail() throws Exception {
        when(userRepository.findById(userId)).thenReturn(Optional.of(seller(true)));
        when(propertyRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                eq(userId), eq(Property.ListingStatus.ACTIVE), any())).thenReturn(List.of());

        String json = MAPPER.writeValueAsString(userService.getPublicProfile(userId));

        // Absent keys, not null values — a serialized "phone": null still advertises
        // the shape and invites a careless mapper edit later (the #31/#43 failure mode).
        assertThat(json).doesNotContain("phone");
        assertThat(json).doesNotContain("email\"");
        assertThat(json).doesNotContain("9876543210");
        assertThat(json).doesNotContain("anitha@propfind.in");
        // The trust signals it DOES carry.
        assertThat(json).contains("isEmailVerified");
        assertThat(json).contains("Anitha R");
    }

    @Test
    void profileCarriesTheOwnerTrustSignals() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(seller(true)));
        when(propertyRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                eq(userId), eq(Property.ListingStatus.ACTIVE), any())).thenReturn(List.of());

        PublicProfileResponse res = userService.getPublicProfile(userId);

        assertThat(res.getId()).isEqualTo(userId);
        assertThat(res.getName()).isEqualTo("Anitha R");
        assertThat(res.getRole()).isEqualTo("SELLER");
        assertThat(res.isEmailVerified()).isTrue();
        assertThat(res.getMemberSince()).isEqualTo(JOINED);
    }

    // ── Only ACTIVE listings ─────────────────────────────────────

    @Test
    void onlyActiveListingsAreCountedAndReturned() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(seller(true)));
        when(propertyRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                eq(userId), eq(Property.ListingStatus.ACTIVE), any()))
            .thenReturn(List.of(activeListing()));
        when(propertyRepository.countByOwnerIdAndStatus(userId, Property.ListingStatus.ACTIVE))
            .thenReturn(7L);
        when(propertyService.toCardResponse(any())).thenReturn(null);

        PublicProfileResponse res = userService.getPublicProfile(userId);

        assertThat(res.getActiveListingCount()).isEqualTo(7L);
        assertThat(res.getListings()).hasSize(1);

        // A seller's drafts, pending-review and rejected listings are their own business —
        // the status is pinned rather than left to the repository's default ordering.
        ArgumentCaptor<Property.ListingStatus> status =
            ArgumentCaptor.forClass(Property.ListingStatus.class);
        verify(propertyRepository)
            .findByOwnerIdAndStatusOrderByCreatedAtDesc(eq(userId), status.capture(), any());
        assertThat(status.getValue()).isEqualTo(Property.ListingStatus.ACTIVE);
    }

    @Test
    void inlineListingsAreCappedEvenWhenTheCountIsLarge() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(seller(true)));
        when(propertyRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                eq(userId), eq(Property.ListingStatus.ACTIVE), any())).thenReturn(List.of());

        userService.getPublicProfile(userId);

        var pageable = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(propertyRepository)
            .findByOwnerIdAndStatusOrderByCreatedAtDesc(eq(userId), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isLessThanOrEqualTo(20);
    }

    // ── Not found ────────────────────────────────────────────────

    @Test
    void unknownIdIs404() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getPublicProfile(userId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivatedAccountIs404_notAnEmptyProfile() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(seller(false)));
        lenient().when(propertyRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                eq(userId), any(), any())).thenReturn(List.of());

        // A banned seller's page must go away, and it must 404 the same way an unknown
        // id does — a distinct response would make the endpoint a ban oracle.
        assertThatThrownBy(() -> userService.getPublicProfile(userId))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
