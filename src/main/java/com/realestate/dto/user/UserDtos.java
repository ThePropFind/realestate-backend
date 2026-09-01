package com.realestate.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.realestate.dto.property.PropertyDtos.PropertyCardResponse;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class UserDtos {

    /**
     * Public seller/agent profile — what a buyer sees behind "View Profile"
     * on the property detail page.
     *
     * SECURITY: this DTO deliberately has no phone and no email field. The owner's
     * phone already reaches a buyer through the detail page's Call / WhatsApp CTAs,
     * which sit behind an active listing; a profile endpoint that repeated it would
     * be a second, cheaper way to harvest contact details for every seller by id.
     * Like {@code DocumentSummaryResponse}, the safety is structural — there is no
     * field to populate, so a careless mapper edit cannot leak one.
     */
    @Data @Builder
    @JsonIgnoreProperties("emailVerified")
    public static class PublicProfileResponse {
        private UUID id;
        private String name;
        private String role;
        private String profilePhotoUrl;
        /** Email confirmed via OTP. NOT an identity check — do not label it "Verified Owner". */
        @JsonProperty("isEmailVerified")
        private boolean isEmailVerified;
        private LocalDateTime memberSince;
        /** Count of ACTIVE listings only — drafts, pending and expired stay private. */
        private long activeListingCount;
        /** The same ACTIVE listings, capped, so the profile screen needs one round trip. */
        private List<PropertyCardResponse> listings;
    }

    /**
     * The signed-in user's own account — what the Settings screen reads.
     *
     * Distinct from {@link PublicProfileResponse} on purpose, and the split is
     * the security boundary: this one carries the email and phone, and is only
     * ever built for the authenticated principal. Keep them separate even
     * though the fields overlap — merging them is exactly how a contact-detail
     * leak gets introduced by a later "reuse the DTO" refactor.
     */
    @Data @Builder
    @JsonIgnoreProperties("emailVerified")
    public static class MeResponse {
        private UUID id;
        private String name;
        private String email;
        private String phone;
        private String role;
        private String profilePhotoUrl;
        @JsonProperty("isEmailVerified")
        private boolean isEmailVerified;
        private LocalDateTime memberSince;
        /** Send the "someone enquired about your listing" email? */
        private boolean notifyEmailInquiries;
    }

    /**
     * Partial update of the signed-in account. Every field is optional —
     * a null means "leave this alone", which is what makes PATCH a PATCH.
     *
     * Deliberately NOT here: email (it is the login identifier and changing it
     * needs a re-verification flow), role (privilege escalation), and password
     * (already served by /auth/forgot-password + /auth/reset-password).
     */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdateMeRequest {
        @Size(min = 1, max = 150, message = "Name must be between 1 and 150 characters")
        private String name;

        /**
         * 10-digit Indian mobile. Blank clears it; the same pattern AuthService
         * uses to tell a phone login from an email one, so a number that does
         * not match here could never be used to sign in.
         */
        @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number")
        private String phone;

        private Boolean notifyEmailInquiries;
    }
}
