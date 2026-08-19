package com.realestate.repository;

import com.realestate.entity.LocalityLandmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LocalityLandmarkRepository extends JpaRepository<LocalityLandmark, UUID> {

    List<LocalityLandmark> findByLocalityIdOrderBySortOrderAsc(UUID localityId);

    /**
     * The fallback set for one city — rows deliberately left unattached to a
     * locality. City-scoped: a city with no fallback rows must return nothing
     * rather than borrowing another city's landmarks.
     */
    List<LocalityLandmark> findByCityIdAndLocalityIsNullOrderBySortOrderAsc(UUID cityId);
}
