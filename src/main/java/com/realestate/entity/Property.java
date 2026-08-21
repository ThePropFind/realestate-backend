package com.realestate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "properties")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Ownership ─────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private User agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locality_id", nullable = false)
    private Locality locality;

    // ── Listing type ──────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false, length = 10)
    private ListingType listingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 30)
    private PropertyType propertyType;

    // ── Content ───────────────────────────────────
    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ── Price ─────────────────────────────────────
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_unit", length = 15)
    @Builder.Default
    private PriceUnit priceUnit = PriceUnit.TOTAL;

    @Column(name = "price_negotiable")
    @Builder.Default
    private boolean priceNegotiable = false;

    @Column(name = "security_deposit", precision = 14, scale = 2)
    private BigDecimal securityDeposit;

    // ── Size ──────────────────────────────────────
    private Short bedrooms;
    private Short bathrooms;
    private Short balconies;

    @Column(name = "total_floors")
    private Short totalFloors;

    @Column(name = "floor_number")
    private Short floorNumber;

    @Column(name = "area_sqft", nullable = false, precision = 10, scale = 2)
    private BigDecimal areaSqft;

    @Column(name = "carpet_area_sqft", precision = 10, scale = 2)
    private BigDecimal carpetAreaSqft;

    // ── Details ───────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "furnishing", length = 20)
    @Builder.Default
    private FurnishingStatus furnishing = FurnishingStatus.UNFURNISHED;

    @Column(length = 20)
    private String facing;

    @Column(name = "age_of_property")
    private Short ageOfProperty;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "parking_available")
    @Builder.Default
    private boolean parkingAvailable = false;

    /**
     * Number of parking slots. Refines {@link #parkingAvailable}, which stays as
     * the coarse flag every existing row and the search filter rely on.
     * Read as: parkingCount != null ? parkingCount : (parkingAvailable ? 1 : 0).
     */
    @Column(name = "parking_count")
    private Short parkingCount;

    /**
     * Ready to move / under construction / new launch. Independent of
     * {@link #availableFrom} (a concrete date) and {@link #ageOfProperty} (years) —
     * neither can imply this without guessing, so it is never derived.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "possession_status", length = 24)
    private PossessionStatus possessionStatus;

    // Who the lister prefers as a tenant — only meaningful for RENT / PG listings.
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_tenant", length = 20)
    private PreferredTenant preferredTenant;

    // ── Location ──────────────────────────────────
    @Column(name = "address_line", columnDefinition = "TEXT")
    private String addressLine;

    // Six-digit Indian PIN. Nullable — pre-V16 listings were never backfilled.
    @Column(length = 6)
    private String pincode;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    // ── Platform flags ────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ListingStatus status = ListingStatus.PENDING_REVIEW;

    @Column(name = "is_featured")
    @Builder.Default
    private boolean isFeatured = false;

    @Column(name = "is_verified")
    @Builder.Default
    private boolean isVerified = false;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * Backs the human-readable reference code buyers quote on a call
     * ("PF000100001"). Assigned by the DB default (nextval on property_ref_seq),
     * so concurrent inserts cannot collide; @Generated(INSERT) omits it from the
     * INSERT and has Hibernate read the generated value straight back.
     * The display string is derived in the mapper — this column stays numeric.
     */
    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    @Column(name = "ref_seq", insertable = false, updatable = false)
    private Long refSeq;

    @Column(name = "views_count")
    @Builder.Default
    private int viewsCount = 0;

    @Column(name = "inquiry_count")
    @Builder.Default
    private int inquiryCount = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Wizard: who is listing? ───────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "listed_by", length = 20)
    private ListedBy listedBy;

    // ── Plot / Land fields ────────────────────────
    @Column(name = "plot_length_ft", precision = 10, scale = 2)
    private BigDecimal plotLengthFt;

    @Column(name = "plot_breadth_ft", precision = 10, scale = 2)
    private BigDecimal plotBreadthFt;

    @Column(name = "plot_area_cents", precision = 10, scale = 4)
    private BigDecimal plotAreaCents;

    @Column(name = "road_width_ft", precision = 6, scale = 2)
    private BigDecimal roadWidthFt;

    @Column(name = "boundary_wall")
    private Boolean boundaryWall;

    @Column(name = "corner_plot")
    private Boolean cornerPlot;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_authority", length = 20)
    private ApprovalAuthority approvalAuthority;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_type", length = 20)
    private OwnershipType ownershipType;

    // ── Agricultural land fields ──────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "soil_type", length = 20)
    private SoilType soilType;

    @Enumerated(EnumType.STRING)
    @Column(name = "water_source", length = 20)
    private WaterSource waterSource;

    @Column(name = "has_well")
    private Boolean hasWell;

    @Enumerated(EnumType.STRING)
    @Column(name = "electric_service", length = 30)
    private ElectricService electricService;

    @Column(name = "crop_currently_grown", length = 200)
    private String cropCurrentlyGrown;

    @Column(name = "fenced")
    private Boolean fenced;

    // ── Promoter fields ───────────────────────────
    @Column(name = "promoter_project_name", length = 200)
    private String promoterProjectName;

    @Column(name = "promoter_years_experience")
    private Short promoterYearsExperience;

    @Column(name = "promoter_total_projects")
    private Short promoterTotalProjects;

    @Column(name = "promoter_cities_active", columnDefinition = "TEXT")
    private String promoterCitiesActive;

    @Column(name = "promoter_rera_id", length = 60)
    private String promoterReraId;

    // ── Relationships ─────────────────────────────
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<PropertyImage> images = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "property_amenities",
        joinColumns = @JoinColumn(name = "property_id"),
        inverseJoinColumns = @JoinColumn(name = "amenity_id")
    )
    @Builder.Default
    private Set<Amenity> amenities = new HashSet<>();

    // ─────────────────────────────────────────────
    // Enums
    // ─────────────────────────────────────────────

    public enum ListingType  { SALE, RENT, PG }

    public enum PropertyType {
        APARTMENT, INDEPENDENT_HOUSE, VILLA,
        PLOT, COMMERCIAL_OFFICE, COMMERCIAL_SHOP,
        BUILDER_FLOOR, PG_HOSTEL, AGRICULTURAL_LAND, WAREHOUSE
    }

    /**
     * Search-side vocabulary for the free-text {@link #facing} column.
     *
     * The column stays a String — it is written by the post wizard and rendered
     * verbatim on the detail screens ("East"), so turning it into an enum column
     * would shout "EAST" at users and reject any legacy value. Search instead
     * matches {@code upper(facing)} against these names, which is case-insensitive
     * by construction and needs no backfill to work.
     */
    public enum Facing {
        NORTH, SOUTH, EAST, WEST,
        NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST
    }

    public enum FurnishingStatus { UNFURNISHED, SEMI_FURNISHED, FULLY_FURNISHED }

    public enum PossessionStatus { READY_TO_MOVE, UNDER_CONSTRUCTION, NEW_LAUNCH }

    public enum PreferredTenant { FAMILY, BACHELOR_MEN, BACHELOR_WOMEN, ANYONE }

    public enum PriceUnit { TOTAL, PER_MONTH, PER_SQFT }

    public enum ListingStatus {
        DRAFT, PENDING_REVIEW, ACTIVE, EXPIRED, REJECTED, SOLD_RENTED
    }

    public enum ListedBy { OWNER, PROMOTER, AGENT }

    public enum ApprovalAuthority { DTCP, CMDA, TNHB, CMA, RERA, LOCAL, OTHER, NONE }

    public enum OwnershipType { SINGLE, JOINT, GIFT, INHERITED, COMPANY, TRUST }

    public enum SoilType { RED, BLACK, ALLUVIAL, LATERITE, SANDY, CLAY, LOAM, OTHER }

    public enum WaterSource { BOREWELL, OPEN_WELL, CANAL, RIVER, RAIN_FED, NONE }

    public enum ElectricService { AVAILABLE_3PHASE, AVAILABLE_1PHASE, AGRI_CONNECTION, NONE }
}
