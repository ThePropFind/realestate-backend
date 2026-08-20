package com.realestate.service;

import com.realestate.dto.property.PropertyDtos.NearbyPlaceResponse;
import com.realestate.entity.City;
import com.realestate.entity.Locality;
import com.realestate.entity.LocalityLandmark;
import com.realestate.repository.LocalityLandmarkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the nearby-places lookup and, most importantly, its fallback.
 *
 * The section must never be empty for a listing in an uncurated locality — that
 * would leave a titled section with nothing under it on the detail page.
 */
@ExtendWith(MockitoExtension.class)
class NearbyPlaceServiceTest {

    @Mock private LocalityLandmarkRepository landmarkRepository;

    @InjectMocks private NearbyPlaceService nearbyPlaceService;

    private final UUID localityId = UUID.randomUUID();
    private final UUID cityId     = UUID.randomUUID();

    private LocalityLandmark landmark(String name, LocalityLandmark.LandmarkKind kind,
                                      String distance, int order) {
        return LocalityLandmark.builder()
            .id(UUID.randomUUID())
            .locality(Locality.builder().id(localityId).build())
            .city(City.builder().id(cityId).build())
            .name(name).kind(kind).distanceLabel(distance)
            .sortOrder((short) order)
            .build();
    }

    @Test
    void returnsTheCuratedSetWhenTheLocalityHasOne() {
        when(landmarkRepository.findByLocalityIdOrderBySortOrderAsc(localityId))
            .thenReturn(List.of(
                landmark("PSG Hospitals", LocalityLandmark.LandmarkKind.HOSPITAL, "2.4 km", 0),
                landmark("PSG Schools",   LocalityLandmark.LandmarkKind.SCHOOL,   "3.1 km", 1)));

        List<NearbyPlaceResponse> res = nearbyPlaceService.forLocality(localityId, cityId);

        assertThat(res).hasSize(2);
        assertThat(res.get(0).getName()).isEqualTo("PSG Hospitals");
        assertThat(res.get(0).getKind()).isEqualTo("HOSPITAL");
        assertThat(res.get(0).getDistanceLabel()).isEqualTo("2.4 km");
        // The city-wide set must not be consulted when curated rows exist.
        verify(landmarkRepository, never())
            .findByCityIdAndLocalityIsNullOrderBySortOrderAsc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fallsBackToTheCityWideSetWhenTheLocalityHasNoCuratedRows() {
        when(landmarkRepository.findByLocalityIdOrderBySortOrderAsc(localityId))
            .thenReturn(List.of());
        when(landmarkRepository.findByCityIdAndLocalityIsNullOrderBySortOrderAsc(cityId))
            .thenReturn(List.of(
                landmark("Brookefields Mall", LocalityLandmark.LandmarkKind.MALL, "5.5 km", 0)));

        List<NearbyPlaceResponse> res = nearbyPlaceService.forLocality(localityId, cityId);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).getName()).isEqualTo("Brookefields Mall");
    }

    @Test
    void usesTheCityFallbackWhenNoLocalityIsGiven() {
        when(landmarkRepository.findByCityIdAndLocalityIsNullOrderBySortOrderAsc(cityId))
            .thenReturn(List.of(
                landmark("Coimbatore Junction", LocalityLandmark.LandmarkKind.TRANSPORT, "6.0 km", 0)));

        assertThat(nearbyPlaceService.forLocality(null, cityId)).hasSize(1);
        verify(landmarkRepository, never()).findByLocalityIdOrderBySortOrderAsc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void neverBorrowsAnotherCitysFallbackWhenThisCityHasNone() {
        // The bug this pins: with a global fallback, a Chennai listing in an
        // uncurated locality was served Coimbatore's landmarks. An empty section
        // is a visible gap; the wrong city's landmarks is a lie presented as fact.
        UUID chennaiId = UUID.randomUUID();
        when(landmarkRepository.findByLocalityIdOrderBySortOrderAsc(localityId)).thenReturn(List.of());
        when(landmarkRepository.findByCityIdAndLocalityIsNullOrderBySortOrderAsc(chennaiId))
            .thenReturn(List.of());

        assertThat(nearbyPlaceService.forLocality(localityId, chennaiId)).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheCityIsUnknown() {
        when(landmarkRepository.findByLocalityIdOrderBySortOrderAsc(localityId)).thenReturn(List.of());

        assertThat(nearbyPlaceService.forLocality(localityId, null)).isEmpty();
    }

    @Test
    void returnsEmptyRatherThanNullWhenNothingIsSeededAtAll() {
        when(landmarkRepository.findByLocalityIdOrderBySortOrderAsc(localityId)).thenReturn(List.of());
        when(landmarkRepository.findByCityIdAndLocalityIsNullOrderBySortOrderAsc(cityId)).thenReturn(List.of());

        assertThat(nearbyPlaceService.forLocality(localityId, cityId)).isNotNull().isEmpty();
    }

    @Test
    void distanceStaysAnOpaqueLabelRatherThanBeingParsedIntoANumber() {
        // "500 m" and "1.2 km" share one column on purpose — these are unsurveyed
        // estimates, and parsing them into a number would invite sorting or
        // filtering that implies a precision the data does not have.
        when(landmarkRepository.findByLocalityIdOrderBySortOrderAsc(localityId))
            .thenReturn(List.of(
                landmark("Trichy Road", LocalityLandmark.LandmarkKind.MALL, "500 m", 0)));

        assertThat(nearbyPlaceService.forLocality(localityId, cityId).get(0).getDistanceLabel())
            .isEqualTo("500 m");
    }
}
