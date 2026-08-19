package com.realestate.repository;

import com.realestate.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID>,
        JpaSpecificationExecutor<Property> {

    /**
     * SELECT ... FOR UPDATE — serialises concurrent uploads on the same property so
     * the image/document caps and sortOrder assignment can't race (#43).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Property p WHERE p.id = :id")
    Optional<Property> findByIdForUpdate(@Param("id") UUID id);

    Page<Property> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);
    Page<Property> findByStatusOrderByCreatedAtAsc(Property.ListingStatus status, Pageable pageable);
    List<Property> findTop6ByStatusAndIsFeaturedTrueOrderByCreatedAtDesc(Property.ListingStatus status);
    List<Property> findTop4ByLocalityIdAndPropertyTypeAndStatusAndIdNot(UUID localityId, Property.PropertyType type, Property.ListingStatus status, UUID excludeId);

    @Modifying
    @Query("UPDATE Property p SET p.viewsCount = p.viewsCount + 1 WHERE p.id = :id")
    void incrementViews(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Property p SET p.inquiryCount = p.inquiryCount + 1 WHERE p.id = :id")
    void incrementInquiryCount(@Param("id") UUID id);

    long countByOwnerIdAndStatus(UUID ownerId, Property.ListingStatus status);

    /**
     * Backs the public owner profile. Status is a parameter rather than hardcoded so the
     * caller states which slice it wants — the public path must only ever pass ACTIVE.
     * Pageable caps the result; a prolific seller must not return an unbounded page.
     */
    List<Property> findByOwnerIdAndStatusOrderByCreatedAtDesc(
            UUID ownerId, Property.ListingStatus status, Pageable pageable);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.status = :status")
    long countByStatus(@Param("status") Property.ListingStatus status);

    @Query("SELECT p.locality.city.name, COUNT(p) FROM Property p " +
           "WHERE p.status = 'ACTIVE' GROUP BY p.locality.city.name " +
           "ORDER BY COUNT(p) DESC")
    List<Object[]> countByCity();
}
