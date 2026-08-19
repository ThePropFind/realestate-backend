package com.realestate.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.realestate.dto.property.PropertyDtos.PropertyCardResponse;
import lombok.Builder;
import lombok.Data;

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
}
