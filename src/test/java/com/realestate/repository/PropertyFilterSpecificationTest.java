package com.realestate.repository;

import com.realestate.dto.property.PropertySearchRequest;
import com.realestate.entity.Property;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The advanced filters added for the redesigned filter screen.
 *
 * Follows the Criteria-mocking style of {@link PropertySpecificationMultiFilterTest}
 * — these assert the *shape* of the query the spec builds, which is what regresses
 * when someone reorders or "simplifies" a predicate.
 *
 * Two behaviours here are load-bearing and easy to break:
 *  - amenities must mean "has ALL of these", via a counting subquery. A plain join
 *    would match a listing owning only one of them and would also multiply rows
 *    against query.distinct(true), corrupting page counts.
 *  - a bounding box must be all-or-nothing. Filtering on latitude alone would
 *    silently return a band across the planet.
 */
class PropertyFilterSpecificationTest {

    @SuppressWarnings("unchecked")
    private static final class Harness {
        final Root<Property> root = mock(Root.class);
        final CriteriaQuery<?> query = mock(CriteriaQuery.class);
        final CriteriaBuilder cb = mock(CriteriaBuilder.class);
        final Predicate pred = mock(Predicate.class);

        Harness() {
            when(cb.equal(any(), any())).thenReturn(pred);
            when(cb.and(any(Predicate[].class))).thenReturn(pred);
            when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(pred);
            when(cb.isTrue(any())).thenReturn(pred);
            when(root.get(any(String.class))).thenAnswer(i -> mock(Path.class));
        }

        void run(PropertySearchRequest req) {
            PropertySpecification.build(req).toPredicate(root, (CriteriaQuery) query, cb);
        }
    }

    // ── Facing ───────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void facing_comparesUpperCasedColumnAgainstEnumNames() {
        Harness h = new Harness();
        Path<Object> facingPath = mock(Path.class);
        Expression<String> upper = mock(Expression.class);
        when(h.root.get("facing")).thenReturn((Path) facingPath);
        when(h.cb.upper(any())).thenReturn(upper);
        when(upper.in(anyCollection())).thenReturn(h.pred);

        PropertySearchRequest req = new PropertySearchRequest();
        req.setFacings(List.of(Property.Facing.EAST, Property.Facing.NORTH_EAST));

        h.run(req);

        // Upper-cased so the free-text column ("East") still matches, and the
        // bound values are the enum NAMES, not the enums themselves.
        verify(h.cb).upper(any());
        verify(upper).in(List.of("EAST", "NORTH_EAST"));
    }

    // ── Amenities ────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void amenities_requireAllOfThem_viaCountingSubquery() {
        Harness h = new Harness();
        Subquery<Long> sub = mock(Subquery.class);
        Root<Property> subRoot = mock(Root.class);
        Join<Object, Object> subJoin = mock(Join.class);
        Path<Object> amenityId = mock(Path.class);

        when(h.query.subquery(Long.class)).thenReturn(sub);
        when(sub.from(Property.class)).thenReturn(subRoot);
        when(subRoot.join("amenities", JoinType.INNER)).thenReturn(subJoin);
        when(subRoot.get(any(String.class))).thenAnswer(i -> mock(Path.class));
        when(subJoin.get("id")).thenReturn(amenityId);
        when(amenityId.in(anyCollection())).thenReturn(h.pred);
        when(sub.select(any())).thenReturn(sub);

        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        PropertySearchRequest req = new PropertySearchRequest();
        req.setAmenityIds(ids);

        h.run(req);

        // The match count must equal the number requested — that is what turns
        // "any of" into "all of".
        verify(h.cb).equal(eq(sub), eq(3L));
        verify(h.cb).countDistinct(amenityId);
    }

    @Test
    void amenities_emptyList_buildsNoSubquery() {
        Harness h = new Harness();
        PropertySearchRequest req = new PropertySearchRequest();
        req.setAmenityIds(List.of());

        h.run(req);

        verify(h.query, never()).subquery(Long.class);
    }

    // ── Localities ───────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void localityIds_joinAndIn() {
        Harness h = new Harness();
        Join<Object, Object> loc = mock(Join.class);
        Path<Object> locId = mock(Path.class);
        when(h.root.join("locality", JoinType.INNER)).thenReturn(loc);
        when(loc.get("id")).thenReturn(locId);
        when(locId.in(anyCollection())).thenReturn(h.pred);

        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        PropertySearchRequest req = new PropertySearchRequest();
        req.setLocalityIds(ids);

        h.run(req);

        verify(locId).in(ids);
    }

    // ── Map viewport ─────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void boundingBox_filtersBothAxes() {
        Harness h = new Harness();
        Path<BigDecimal> lat = mock(Path.class);
        Path<BigDecimal> lng = mock(Path.class);
        when(h.root.get("latitude")).thenReturn((Path) lat);
        when(h.root.get("longitude")).thenReturn((Path) lng);
        when(h.cb.between(any(), any(BigDecimal.class), any(BigDecimal.class))).thenReturn(h.pred);

        PropertySearchRequest req = new PropertySearchRequest();
        req.setSwLat(new BigDecimal("10.9")); req.setNeLat(new BigDecimal("11.1"));
        req.setSwLng(new BigDecimal("76.8")); req.setNeLng(new BigDecimal("77.1"));

        h.run(req);

        // SW is the low corner, NE the high one — swapping them yields an empty box.
        verify(h.cb).between(eq(lat), eq(new BigDecimal("10.9")), eq(new BigDecimal("11.1")));
        verify(h.cb).between(eq(lng), eq(new BigDecimal("76.8")), eq(new BigDecimal("77.1")));
    }

    @Test
    void boundingBox_partial_isIgnoredEntirely() {
        Harness h = new Harness();
        PropertySearchRequest req = new PropertySearchRequest();
        req.setNeLat(new BigDecimal("11.1"));   // only one corner supplied

        h.run(req);

        verify(h.cb, never()).between(any(), any(BigDecimal.class), any(BigDecimal.class));
    }

    // ── Boolean flags ────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void parkingRequired_acceptsEitherTheFlagOrACount() {
        Harness h = new Harness();
        Path<Object> count = mock(Path.class);
        Expression<Integer> asInt = mock(Expression.class);
        when(h.root.get("parkingCount")).thenReturn((Path) count);
        when(count.as(Integer.class)).thenReturn(asInt);
        when(h.cb.greaterThan(any(), any(Integer.class))).thenReturn(h.pred);

        PropertySearchRequest req = new PropertySearchRequest();
        req.setParkingRequired(true);

        h.run(req);

        // Legacy rows only have the boolean; newer ones carry a count.
        verify(h.cb).or(any(Predicate.class), any(Predicate.class));
        verify(h.cb).greaterThan(eq(asInt), eq(0));
    }

    @Test
    void verifiedAndNegotiable_onlyFilterWhenTrue() {
        Harness onlyFalse = new Harness();
        PropertySearchRequest req = new PropertySearchRequest();
        req.setVerifiedOnly(false);
        req.setNegotiableOnly(false);

        onlyFalse.run(req);

        // false must behave as "don't care", not as "must be false".
        verify(onlyFalse.cb, never()).isTrue(any());
    }
}
