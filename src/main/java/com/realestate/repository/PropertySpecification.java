package com.realestate.repository;

import com.realestate.dto.property.PropertySearchRequest;
import com.realestate.entity.Property;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a dynamic JPA query from a PropertySearchRequest.
 *
 * Every filter field is optional — only non-null values are added
 * to the WHERE clause. This is how Spring Data JPA handles
 * "search with optional filters" cleanly without messy if/else SQL.
 *
 * Usage:
 *   Specification<Property> spec = PropertySpecification.build(request);
 *   Page<Property> results = propertyRepository.findAll(spec, pageable);
 */
public class PropertySpecification {

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    /**
     * Admin listings filter — unlike {@link #build}, it does NOT pin status to ACTIVE:
     * admins see every status. Optional status filter + optional free-text search
     * across title, locality name, and city name.
     */
    public static Specification<Property> adminFilter(Property.ListingStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                Join<Object, Object> locality = root.join("locality", JoinType.LEFT);
                Join<Object, Object> city     = locality.join("city", JoinType.LEFT);
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")),    pattern),
                    cb.like(cb.lower(locality.get("name")), pattern),
                    cb.like(cb.lower(city.get("name")),     pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Property> build(PropertySearchRequest req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter to ACTIVE listings for public search
            predicates.add(cb.equal(root.get("status"), Property.ListingStatus.ACTIVE));

            // ── Listing type (sale / rent / pg) ──────────
            if (req.getListingType() != null) {
                predicates.add(cb.equal(root.get("listingType"), req.getListingType()));
            }
            if (req.getListingTypes() != null && !req.getListingTypes().isEmpty()) {
                predicates.add(root.get("listingType").in(req.getListingTypes()));
            }

            // ── Property type (apartment / villa / etc.) ─
            if (req.getPropertyType() != null) {
                predicates.add(cb.equal(root.get("propertyType"), req.getPropertyType()));
            }
            if (req.getPropertyTypes() != null && !req.getPropertyTypes().isEmpty()) {
                predicates.add(root.get("propertyType").in(req.getPropertyTypes()));
            }

            // ── City (join through locality → city) ───────
            if (req.getCitySlug() != null && !req.getCitySlug().isBlank()) {
                Join<Object, Object> locality = root.join("locality", JoinType.INNER);
                Join<Object, Object> city = locality.join("city", JoinType.INNER);
                predicates.add(cb.equal(city.get("slug"), req.getCitySlug()));
            }

            // ── Locality ──────────────────────────────────
            if (req.getLocalityId() != null) {
                predicates.add(cb.equal(
                    root.get("locality").get("id"), req.getLocalityId()
                ));
            }

            // ── Price range ───────────────────────────────
            if (req.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), req.getMinPrice()));
            }
            if (req.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), req.getMaxPrice()));
            }

            // ── Bedrooms ──────────────────────────────────
            if (req.getMinBedrooms() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("bedrooms"), req.getMinBedrooms().shortValue()
                ));
            }
            if (req.getMaxBedrooms() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("bedrooms"), req.getMaxBedrooms().shortValue()
                ));
            }

            // ── Area ──────────────────────────────────────
            if (req.getMinArea() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("areaSqft"), req.getMinArea()));
            }
            if (req.getMaxArea() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("areaSqft"), req.getMaxArea()));
            }

            // ── Furnishing ────────────────────────────────
            if (req.getFurnishing() != null) {
                predicates.add(cb.equal(root.get("furnishing"), req.getFurnishing()));
            }
            if (req.getFurnishings() != null && !req.getFurnishings().isEmpty()) {
                predicates.add(root.get("furnishing").in(req.getFurnishings()));
            }

            // ── Featured only ─────────────────────────────
            if (Boolean.TRUE.equals(req.getFeaturedOnly())) {
                predicates.add(cb.isTrue(root.get("isFeatured")));
            }

            // ── Multi-select localities ───────────────────
            if (notEmpty(req.getLocalityIds())) {
                Join<Object, Object> locJoin = root.join("locality", JoinType.INNER);
                predicates.add(locJoin.get("id").in(req.getLocalityIds()));
            }

            // ── Possession / posted by / approval ─────────
            if (notEmpty(req.getPossessionStatuses())) {
                predicates.add(root.get("possessionStatus").in(req.getPossessionStatuses()));
            }
            if (notEmpty(req.getListedBys())) {
                predicates.add(root.get("listedBy").in(req.getListedBys()));
            }
            if (notEmpty(req.getApprovalAuthorities())) {
                predicates.add(root.get("approvalAuthority").in(req.getApprovalAuthorities()));
            }

            // ── Facing ────────────────────────────────────
            // The column is free text ("East"), so compare upper-cased against the
            // enum names rather than binding the enum directly.
            if (notEmpty(req.getFacings())) {
                List<String> names = req.getFacings().stream().map(Enum::name).toList();
                predicates.add(cb.upper(root.get("facing")).in(names));
            }

            // ── Rooms / floor / age ───────────────────────
            if (req.getMinBathrooms() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("bathrooms").as(Integer.class), req.getMinBathrooms()));
            }
            if (req.getMaxFloor() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("floorNumber").as(Integer.class), req.getMaxFloor()));
            }
            if (req.getMaxAge() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("ageOfProperty").as(Integer.class), req.getMaxAge()));
            }

            // ── Boolean flags ─────────────────────────────
            // parkingCount is the newer column and wins when set; fall back to the
            // legacy boolean, mirroring the display rule on the entity.
            if (Boolean.TRUE.equals(req.getParkingRequired())) {
                predicates.add(cb.or(
                    cb.isTrue(root.get("parkingAvailable")),
                    cb.greaterThan(root.get("parkingCount").as(Integer.class), 0)
                ));
            }
            if (Boolean.TRUE.equals(req.getVerifiedOnly())) {
                predicates.add(cb.isTrue(root.get("isVerified")));
            }
            if (Boolean.TRUE.equals(req.getNegotiableOnly())) {
                predicates.add(cb.isTrue(root.get("priceNegotiable")));
            }

            // ── Map viewport ──────────────────────────────
            // All four corners required; a partial box is ignored rather than
            // silently filtering on one axis.
            if (req.getNeLat() != null && req.getNeLng() != null
                && req.getSwLat() != null && req.getSwLng() != null) {
                predicates.add(cb.between(root.get("latitude"),  req.getSwLat(), req.getNeLat()));
                predicates.add(cb.between(root.get("longitude"), req.getSwLng(), req.getNeLng()));
            }

            // ── Amenities — "has ALL of these", not any-of ─
            // A plain join would return a listing matching only one of them, and
            // would multiply rows against the query.distinct(true) below (breaking
            // page counts). A correlated COUNT subquery keeps one row per property.
            if (notEmpty(req.getAmenityIds()) && query != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Property> subRoot = sub.from(Property.class);
                Join<Object, Object> subAmenity = subRoot.join("amenities", JoinType.INNER);
                sub.select(cb.countDistinct(subAmenity.get("id")))
                   .where(
                       cb.equal(subRoot.get("id"), root.get("id")),
                       subAmenity.get("id").in(req.getAmenityIds())
                   );
                predicates.add(cb.equal(sub, (long) req.getAmenityIds().size()));
            }

            // ── Keyword search (title, description, or city name) ─────
            if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
                String pattern = "%" + req.getKeyword().toLowerCase() + "%";
                Predicate titleMatch = cb.like(
                    cb.lower(root.get("title")), pattern
                );
                Predicate descMatch = cb.like(
                    cb.lower(root.get("description")), pattern
                );
                // Also match locality + city names so typing "Saibaba Colony" or
                // "Coimbatore" in search finds those properties
                Join<Object, Object> kwLocality = root.join("locality", JoinType.LEFT);
                Join<Object, Object> kwCity = kwLocality.join("city", JoinType.LEFT);
                Predicate localityNameMatch = cb.like(cb.lower(kwLocality.get("name")), pattern);
                Predicate cityNameMatch = cb.like(cb.lower(kwCity.get("name")), pattern);
                predicates.add(cb.or(titleMatch, descMatch, localityNameMatch, cityNameMatch));
            }

            // Avoid N+1 on images fetch for listing cards
            if (query != null) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
