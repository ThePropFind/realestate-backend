package com.realestate.dto.property;

import com.realestate.entity.Property;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Represents all the optional query parameters for property search.
 *
 * Example URL:
 * GET /api/search/properties?citySlug=coimbatore&listingType=RENT
 *   &propertyType=APARTMENT&minPrice=10000&maxPrice=30000
 *   &minBedrooms=2&furnishing=SEMI_FURNISHED&page=0&size=20&sort=price,asc
 *
 * Spring automatically maps request params to this class fields.
 */
@Data
public class PropertySearchRequest {

    // Location
    private String citySlug;
    private UUID   localityId;
    /** Multi-select localities — OR'd together. */
    private List<UUID> localityIds;

    // Type filters — single value (kept for backward compat / simple links)
    private Property.ListingType    listingType;
    private Property.PropertyType   propertyType;

    // Multi-select filters — when non-empty, matched with IN (union).
    // Combine with the single-value fields above via AND.
    private List<Property.ListingType>      listingTypes;
    private List<Property.PropertyType>     propertyTypes;
    private List<Property.FurnishingStatus> furnishings;

    // Price
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    // Size
    private Integer minBedrooms;
    private Integer maxBedrooms;
    private BigDecimal minArea;
    private BigDecimal maxArea;

    // Other filters
    private Property.FurnishingStatus furnishing;
    private Boolean featuredOnly;

    // ── Advanced filters (the redesigned filter screen) ──────────
    // All multi-selects are OR within a group, AND across groups.
    private List<Property.PossessionStatus>   possessionStatuses;
    private List<Property.ListedBy>           listedBys;
    private List<Property.Facing>             facings;
    private List<Property.ApprovalAuthority>  approvalAuthorities;

    /** "Has ALL of these" — not any-of. See PropertySpecification. */
    private List<UUID> amenityIds;

    private Integer minBathrooms;
    /** Highest acceptable floor number (e.g. "ground to 3rd"). */
    private Integer maxFloor;
    /** Highest acceptable age in years; 0 means new construction only. */
    private Integer maxAge;

    private Boolean parkingRequired;
    private Boolean verifiedOnly;
    private Boolean negotiableOnly;

    // Map viewport (bounding box) — all four are required together.
    private BigDecimal neLat;
    private BigDecimal neLng;
    private BigDecimal swLat;
    private BigDecimal swLng;

    // Keyword
    private String keyword;
}
