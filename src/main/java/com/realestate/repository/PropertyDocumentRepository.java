package com.realestate.repository;

import com.realestate.entity.PropertyDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PropertyDocumentRepository extends JpaRepository<PropertyDocument, UUID> {
    List<PropertyDocument> findByPropertyId(UUID propertyId);
    boolean existsByPropertyId(UUID propertyId);
    void deleteByPropertyId(UUID propertyId);
}
