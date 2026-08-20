package com.realestate.service;

import com.realestate.dto.property.PropertyDtos.PropertyDetailResponse;
import com.realestate.entity.City;
import com.realestate.entity.Locality;
import com.realestate.entity.Property;
import com.realestate.entity.User;
import com.realestate.repository.PropertyDocumentRepository;
import com.realestate.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins the reference code and the two other B2 fields.
 *
 * The code is DERIVED from the ref_seq column, never stored, so there is one
 * definition of the format. ref_seq itself comes from a Postgres sequence via a
 * column default, which is what makes concurrent inserts collision-free — that
 * part is a DB guarantee and is verified against a live database, not here.
 */
@ExtendWith(MockitoExtension.class)
class PropertyReferenceCodeTest {

    @Mock private PropertyRepository         propertyRepository;
    @Mock private PropertyDocumentRepository documentRepository;
    @Mock private NearbyPlaceService         nearbyPlaceService;

    @InjectMocks private PropertyService propertyService;

    private final UUID propertyId = UUID.randomUUID();

    private Property.PropertyBuilder listing() {
        User owner = User.builder()
            .id(UUID.randomUUID()).name("Anitha R").email("owner@propfind.in")
            .role(User.Role.SELLER).build();
        City city = City.builder().id(UUID.randomUUID()).name("Coimbatore").slug("coimbatore").build();
        Locality locality = Locality.builder()
            .id(UUID.randomUUID()).name("Gandhipuram").slug("gandhipuram").city(city).build();

        return Property.builder()
            .id(propertyId).owner(owner).locality(locality)
            .title("3BHK Individual Villa")
            .listingType(Property.ListingType.SALE)
            .propertyType(Property.PropertyType.VILLA)
            .status(Property.ListingStatus.ACTIVE)
            .price(new BigDecimal("9000000"))
            .priceUnit(Property.PriceUnit.TOTAL)
            .furnishing(Property.FurnishingStatus.SEMI_FURNISHED)
            .areaSqft(new BigDecimal("1938"))
            .images(List.of()).amenities(Set.of());
    }

    private PropertyDetailResponse detailOf(Property p) {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(p));
        when(documentRepository.findByPropertyId(propertyId)).thenReturn(List.of());
        return propertyService.getById(propertyId);
    }

    // ── Reference code ───────────────────────────────────────────

    @Test
    void referenceCode_isPfPrefixedAndZeroPaddedToNineDigits() {
        assertThat(detailOf(listing().refSeq(100_001L).build()).getReferenceCode())
            .isEqualTo("PF000100001");
    }

    @Test
    void referenceCode_padsSmallSequenceValues() {
        assertThat(detailOf(listing().refSeq(7L).build()).getReferenceCode())
            .isEqualTo("PF000000007");
    }

    @Test
    void referenceCode_doesNotTruncateAValueWiderThanNineDigits() {
        // Padding is a minimum width, not a cap — a 10-digit sequence must still
        // round-trip intact rather than silently colliding with a shorter code.
        assertThat(detailOf(listing().refSeq(1_234_567_890L).build()).getReferenceCode())
            .isEqualTo("PF1234567890");
    }

    @Test
    void referenceCode_isNullWhenTheRowHasNotBeenFlushedYet() {
        assertThat(detailOf(listing().refSeq(null).build()).getReferenceCode()).isNull();
    }

    // ── Possession status ────────────────────────────────────────

    @Test
    void possessionStatus_serializesAsItsEnumName() {
        assertThat(detailOf(listing()
            .possessionStatus(Property.PossessionStatus.READY_TO_MOVE).build())
            .getPossessionStatus()).isEqualTo("READY_TO_MOVE");
    }

    @Test
    void possessionStatus_staysNullRatherThanBeingDerivedFromAge() {
        // A listing with an age but no stated possession must NOT be reported as
        // ready to move — that would be a claim the seller never made.
        Property p = listing().ageOfProperty((short) 5).possessionStatus(null).build();
        assertThat(detailOf(p).getPossessionStatus()).isNull();
    }

    // ── Parking count ────────────────────────────────────────────

    @Test
    void parkingCount_isReturnedWhenTheSellerGaveOne() {
        Property p = listing().parkingAvailable(true).parkingCount((short) 2).build();
        PropertyDetailResponse res = detailOf(p);
        assertThat(res.getParkingCount()).isEqualTo(2);
        assertThat(res.isParkingAvailable()).isTrue();
    }

    @Test
    void parkingCount_isNullWhenAbsentSoClientsFallBackToTheFlag() {
        Property p = listing().parkingAvailable(true).parkingCount(null).build();
        PropertyDetailResponse res = detailOf(p);
        assertThat(res.getParkingCount()).isNull();
        assertThat(res.isParkingAvailable()).isTrue();
    }
}
