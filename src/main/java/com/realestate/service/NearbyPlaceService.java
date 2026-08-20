package com.realestate.service;

import com.realestate.dto.property.PropertyDtos.NearbyPlaceResponse;
import com.realestate.entity.LocalityLandmark;
import com.realestate.repository.LocalityLandmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Serves the curated "nearby places" set for a locality.
 *
 * Cached like the other near-static lookups (cities, amenities): the rows only
 * change by migration, and a migration restarts the app, which clears the simple
 * in-memory cache. No evict path is needed because nothing mutates these at runtime.
 */
@Service
@RequiredArgsConstructor
public class NearbyPlaceService {

    private final LocalityLandmarkRepository landmarkRepository;

    /**
     * Landmarks for a locality, falling back to that locality's CITY set when the
     * locality has no curated rows of its own.
     *
     * The fallback is city-scoped on purpose. A global fallback would serve
     * Coimbatore landmarks to a listing in Chennai — an empty section is a gap a
     * buyer can see through, but the wrong city's landmarks is a lie the page
     * presents as fact. A city with no fallback rows returns an empty list.
     */
    @Cacheable(value = "nearbyPlaces", key = "#localityId + '|' + #cityId")
    @Transactional(readOnly = true)
    public List<NearbyPlaceResponse> forLocality(UUID localityId, UUID cityId) {
        if (localityId != null) {
            List<LocalityLandmark> curated = landmarkRepository.findByLocalityIdOrderBySortOrderAsc(localityId);
            if (!curated.isEmpty()) return toResponse(curated);
        }
        if (cityId == null) return List.of();
        return toResponse(landmarkRepository.findByCityIdAndLocalityIsNullOrderBySortOrderAsc(cityId));
    }

    private List<NearbyPlaceResponse> toResponse(List<LocalityLandmark> landmarks) {
        return landmarks.stream()
            .map(l -> NearbyPlaceResponse.builder()
                .name(l.getName())
                .kind(l.getKind().name())
                .distanceLabel(l.getDistanceLabel())
                .build())
            .toList();
    }
}
