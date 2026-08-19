package com.realestate.dto.property;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.realestate.entity.Property;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PropertyDtos {

    // ─────────────────────────────────────────────
    // REQUEST: Create / Update a property
    // ─────────────────────────────────────────────

    @Data
    public static class PropertyRequest {

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title is too long")
        private String title;

        @Size(max = 5000, message = "Description too long")
        private String description;

        @NotNull(message = "Listing type is required (SALE, RENT, PG)")
        private Property.ListingType listingType;

        @NotNull(message = "Property type is required")
        private Property.PropertyType propertyType;

        @NotNull(message = "Locality ID is required")
        private UUID localityId;

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
        private BigDecimal price;

        private Property.PriceUnit priceUnit;
        private boolean priceNegotiable;
        private BigDecimal securityDeposit;

        @Min(value = 0, message = "Bedrooms cannot be negative")
        @Max(value = 20, message = "Bedrooms value seems too high")
        private Integer bedrooms;

        private Integer bathrooms;
        private Integer balconies;
        private Integer totalFloors;
        private Integer floorNumber;

        @NotNull(message = "Area is required")
        @DecimalMin(value = "1.0", message = "Area must be at least 1 sqft")
        private BigDecimal areaSqft;

        private BigDecimal carpetAreaSqft;
        private Property.FurnishingStatus furnishing;
        private String facing;
        private Integer ageOfProperty;
        private LocalDate availableFrom;
        private boolean parkingAvailable;

        @Min(value = 0, message = "Parking count cannot be negative")
        @Max(value = 20, message = "Parking count looks wrong")
        private Integer parkingCount;

        private Property.PossessionStatus possessionStatus;

        // Only meaningful for RENT / PG; frontend sends it only for those listing types.
        private Property.PreferredTenant preferredTenant;

        @NotBlank(message = "Full address is required")
        private String addressLine;
        private BigDecimal latitude;
        private BigDecimal longitude;

        // IDs of amenities from the amenities master table
        private List<UUID> amenityIds;

        // ── Phase B wizard extensions ─────────────
        private Property.ListedBy listedBy;

        // Plot / land
        private BigDecimal plotLengthFt;
        private BigDecimal plotBreadthFt;
        private BigDecimal plotAreaCents;
        private BigDecimal roadWidthFt;
        private Boolean boundaryWall;
        private Boolean cornerPlot;
        private Property.ApprovalAuthority approvalAuthority;
        private Property.OwnershipType ownershipType;

        // Agri
        private Property.SoilType soilType;
        private Property.WaterSource waterSource;
        private Boolean hasWell;
        private Property.ElectricService electricService;
        private String cropCurrentlyGrown;
        private Boolean fenced;

        // Promoter
        private String promoterProjectName;
        private Integer promoterYearsExperience;
        private Integer promoterTotalProjects;
        private String promoterCitiesActive;
        private String promoterReraId;
    }

    // ─────────────────────────────────────────────
    // RESPONSE: Property card (list view)
    // Lightweight — no description, fewer fields
    // ─────────────────────────────────────────────

    @Data @Builder
    @JsonIgnoreProperties({"featured", "verified"})
    public static class PropertyCardResponse {
        private UUID   id;
        private String title;
        private String listingType;
        private String propertyType;
        private String status;
        private BigDecimal price;
        private String priceUnit;
        private Integer bedrooms;
        private Integer bathrooms;
        private BigDecimal areaSqft;
        private String furnishing;
        private String localityName;
        private String cityName;
        private BigDecimal latitude;
        private BigDecimal longitude;
        @JsonProperty("isFeatured")
        private boolean isFeatured;
        @JsonProperty("isVerified")
        private boolean isVerified;
        private String primaryImageUrl;
        private int viewsCount;
        private LocalDateTime createdAt;
    }

    // ─────────────────────────────────────────────
    // RESPONSE: Full property detail page
    // ─────────────────────────────────────────────

    @Data @Builder
    @JsonIgnoreProperties({"featured", "verified"})
    public static class PropertyDetailResponse {
        private UUID   id;
        private String title;
        private String description;
        private String listingType;
        private String propertyType;
        private String status;
        private String rejectionReason;

        private BigDecimal price;
        private String priceUnit;
        private boolean priceNegotiable;
        private BigDecimal securityDeposit;

        private Integer bedrooms;
        private Integer bathrooms;
        private Integer balconies;
        private Integer totalFloors;
        private Integer floorNumber;
        private BigDecimal areaSqft;
        private BigDecimal carpetAreaSqft;

        private String furnishing;
        private String facing;
        private Integer ageOfProperty;
        private LocalDate availableFrom;
        private boolean parkingAvailable;
        /** Null when the seller never gave a count — clients fall back to parkingAvailable. */
        private Integer parkingCount;
        private String possessionStatus;
        private String preferredTenant;
        /** Human-readable listing reference, e.g. "PF000100001". Server-assigned. */
        private String referenceCode;

        private String addressLine;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String localityName;
        private String localitySlug;
        private String cityName;
        private String citySlug;

        @JsonProperty("isFeatured")
        private boolean isFeatured;
        @JsonProperty("isVerified")
        private boolean isVerified;
        private int viewsCount;
        private int inquiryCount;

        private List<ImageResponse> images;
        private List<AmenityResponse> amenities;
        private OwnerInfo owner;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;

        // ── Phase B wizard extensions ─────────────
        private String listedBy;

        private BigDecimal plotLengthFt;
        private BigDecimal plotBreadthFt;
        private BigDecimal plotAreaCents;
        private BigDecimal roadWidthFt;
        private Boolean boundaryWall;
        private Boolean cornerPlot;
        private String approvalAuthority;
        private String ownershipType;

        private String soilType;
        private String waterSource;
        private Boolean hasWell;
        private String electricService;
        private String cropCurrentlyGrown;
        private Boolean fenced;

        private String promoterProjectName;
        private Integer promoterYearsExperience;
        private Integer promoterTotalProjects;
        private String promoterCitiesActive;
        private String promoterReraId;

        /** Curated nearby places for this listing's locality. Never null; may be empty. */
        private List<NearbyPlaceResponse> nearby;

        /**
         * Verification documents. Typed as the view interface, not as
         * {@link DocumentResponse}, so the public projection is incapable of
         * carrying a storage URL: the public endpoint builds
         * {@link DocumentSummaryResponse}, which has no url field to populate.
         * Owner and admin endpoints build the full {@link DocumentResponse}.
         */
        private List<PropertyDocumentView> documents;
    }

    /**
     * Marker for the two document projections. Jackson serializes the concrete
     * runtime type, so a summary emits two keys and the full response emits four.
     */
    public interface PropertyDocumentView {}

    /**
     * Public projection — never carries the storage URL or the document id.
     * Verification documents hold PII (survey numbers, EC entries, patta records)
     * and live in a private bucket read only through an admin presigned download.
     */
    @Data @Builder
    public static class DocumentSummaryResponse implements PropertyDocumentView {
        private String docType;
        private String label;
    }

    /** Owner/admin projection — includes the id the presigned download is keyed on. */
    @Data @Builder
    public static class DocumentResponse implements PropertyDocumentView {
        private UUID id;
        private String docType;
        private String url;
        private String label;
    }

    @Data @Builder
    public static class DocumentDownloadResponse {
        private String url;
        private int    expiresIn;   // seconds the signed URL stays valid
        private String docType;
    }

    // ─────────────────────────────────────────────
    // Nested response types
    // ─────────────────────────────────────────────

    @Data @Builder
    @JsonIgnoreProperties("primary")
    public static class ImageResponse {
        private UUID id;
        private String url;
        @JsonProperty("isPrimary")
        private boolean isPrimary;
        private int sortOrder;
    }

    /**
     * A curated nearby place. distanceLabel is a human-readable string
     * ("2.4 km"), not a number — these are realistic but unsurveyed, and the
     * type keeps clients from presenting them as precise measurements.
     */
    @Data @Builder
    public static class NearbyPlaceResponse {
        private String name;
        private String kind;
        private String distanceLabel;
    }

    @Data @Builder
    public static class AmenityResponse {
        private UUID id;
        private String name;
        private String category;
        private String iconKey;
    }

    @Data @Builder
    @JsonIgnoreProperties("emailVerified")
    public static class OwnerInfo {
        private UUID id;
        private String name;
        private String phone;
        private String profilePhotoUrl;
        private String role;
        /**
         * Email confirmed via OTP (User.verified, set only by AuthService.verifyOtp).
         * This is NOT an identity check — clients must not label it "Verified Owner".
         */
        @JsonProperty("isEmailVerified")
        private boolean isEmailVerified;
        /** Account creation date — backs the "Member since" line on the owner card. */
        private LocalDateTime memberSince;
    }

    // ─────────────────────────────────────────────
    // Admin: status update
    // ─────────────────────────────────────────────

    @Data
    public static class StatusUpdateRequest {
        private Property.ListingStatus status;
        private String rejectionReason;  // required only when status = REJECTED
    }
}
