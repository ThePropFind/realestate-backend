package com.realestate.repository;

import com.realestate.entity.PropertyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PropertyReportRepository extends JpaRepository<PropertyReport, UUID> {

    /** Backs "how many times has this listing been flagged" when moderation lands. */
    long countByPropertyIdAndStatus(UUID propertyId, PropertyReport.Status status);
}
