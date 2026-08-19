package com.realestate.service;

import com.realestate.dto.property.PropertyDtos.*;
import com.realestate.dto.property.PropertySearchRequest;
import com.realestate.entity.*;
import com.realestate.exception.BadRequestException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.exception.UnauthorizedException;
import com.realestate.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyService {

    private final PropertyRepository         propertyRepository;
    private final PropertyImageRepository    imageRepository;
    private final PropertyDocumentRepository documentRepository;
    private final LocalityRepository         localityRepository;
    private final AmenityRepository          amenityRepository;
    private final UserRepository             userRepository;
    private final StorageService             imageUploadService;
    private final EmailService               emailService;
    private final NearbyPlaceService         nearbyPlaceService;

    private static final int MAX_IMAGES_PER_PROPERTY    = 20;
    private static final int MAX_DOCUMENTS_PER_PROPERTY = 8;

    // ─────────────────────────────────────────────
    // SEARCH (public)
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PropertyCardResponse> searchProperties(
            PropertySearchRequest req, Pageable pageable) {

        return propertyRepository
            .findAll(PropertySpecification.build(req), pageable)
            .map(this::toCardResponse);
    }

    // ─────────────────────────────────────────────
    // GET BY ID (public — increments view count)
    // ─────────────────────────────────────────────

    @Transactional
    public PropertyDetailResponse getById(UUID id) {
        Property property = findActiveProperty(id);
        propertyRepository.incrementViews(id);
        // Public endpoint — never expose verification document URLs.
        // Docs contain owner PII (survey numbers, EC details, patta etc.).
        return toDetailResponse(property, false);
    }

    // ─────────────────────────────────────────────
    // FEATURED (homepage)
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PropertyCardResponse> getFeatured() {
        return propertyRepository
            .findTop6ByStatusAndIsFeaturedTrueOrderByCreatedAtDesc(Property.ListingStatus.ACTIVE)
            .stream().map(this::toCardResponse).toList();
    }

    // ─────────────────────────────────────────────
    // SIMILAR PROPERTIES
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PropertyCardResponse> getSimilar(UUID propertyId) {
        Property property = findActiveProperty(propertyId);
        return propertyRepository
            .findTop4ByLocalityIdAndPropertyTypeAndStatusAndIdNot(
                property.getLocality().getId(),
                property.getPropertyType(),
                Property.ListingStatus.ACTIVE,
                propertyId
            )
            .stream().map(this::toCardResponse).toList();
    }

    // ─────────────────────────────────────────────
    // CREATE LISTING
    // ─────────────────────────────────────────────

    @Transactional
    public PropertyDetailResponse create(PropertyRequest req, String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        Locality locality = localityRepository.findById(req.getLocalityId())
            .orElseThrow(() -> new ResourceNotFoundException("Locality", req.getLocalityId()));

        Set<Amenity> amenities = new HashSet<>();
        if (req.getAmenityIds() != null && !req.getAmenityIds().isEmpty()) {
            amenities = new HashSet<>(amenityRepository.findByIdIn(req.getAmenityIds()));
        }

        Property property = Property.builder()
            .owner(owner)
            .locality(locality)
            .listingType(req.getListingType())
            .propertyType(req.getPropertyType())
            .title(req.getTitle())
            .description(req.getDescription())
            .price(req.getPrice())
            .priceUnit(req.getPriceUnit() != null ? req.getPriceUnit() : Property.PriceUnit.TOTAL)
            .priceNegotiable(req.isPriceNegotiable())
            .securityDeposit(req.getSecurityDeposit())
            .bedrooms(req.getBedrooms() != null ? req.getBedrooms().shortValue() : null)
            .bathrooms(req.getBathrooms() != null ? req.getBathrooms().shortValue() : null)
            .balconies(req.getBalconies() != null ? req.getBalconies().shortValue() : null)
            .totalFloors(req.getTotalFloors() != null ? req.getTotalFloors().shortValue() : null)
            .floorNumber(req.getFloorNumber() != null ? req.getFloorNumber().shortValue() : null)
            .areaSqft(req.getAreaSqft())
            .carpetAreaSqft(req.getCarpetAreaSqft())
            .furnishing(req.getFurnishing() != null
                ? req.getFurnishing() : Property.FurnishingStatus.UNFURNISHED)
            .facing(req.getFacing())
            .ageOfProperty(req.getAgeOfProperty() != null
                ? req.getAgeOfProperty().shortValue() : null)
            .availableFrom(req.getAvailableFrom())
            .parkingAvailable(req.isParkingAvailable())
            .parkingCount(req.getParkingCount() != null ? req.getParkingCount().shortValue() : null)
            .possessionStatus(req.getPossessionStatus())
            .preferredTenant(req.getPreferredTenant())
            .addressLine(req.getAddressLine())
            .latitude(req.getLatitude())
            .longitude(req.getLongitude())
            .amenities(amenities)
            .listedBy(req.getListedBy() != null ? req.getListedBy() : Property.ListedBy.OWNER)
            .plotLengthFt(req.getPlotLengthFt())
            .plotBreadthFt(req.getPlotBreadthFt())
            .plotAreaCents(req.getPlotAreaCents())
            .roadWidthFt(req.getRoadWidthFt())
            .boundaryWall(req.getBoundaryWall())
            .cornerPlot(req.getCornerPlot())
            .approvalAuthority(req.getApprovalAuthority())
            .ownershipType(req.getOwnershipType())
            .soilType(req.getSoilType())
            .waterSource(req.getWaterSource())
            .hasWell(req.getHasWell())
            .electricService(req.getElectricService())
            .cropCurrentlyGrown(req.getCropCurrentlyGrown())
            .fenced(req.getFenced())
            .promoterProjectName(req.getPromoterProjectName())
            .promoterYearsExperience(req.getPromoterYearsExperience() != null
                ? req.getPromoterYearsExperience().shortValue() : null)
            .promoterTotalProjects(req.getPromoterTotalProjects() != null
                ? req.getPromoterTotalProjects().shortValue() : null)
            .promoterCitiesActive(req.getPromoterCitiesActive())
            .promoterReraId(req.getPromoterReraId())
            .status(Property.ListingStatus.PENDING_REVIEW)
            .expiresAt(LocalDateTime.now().plusDays(90))   // listing valid for 90 days
            .build();

        // saveAndFlush, not save: ref_seq is assigned by the DB default and read back
        // by @Generated(INSERT). A deferred flush would return referenceCode = null,
        // since the UUID id is assigned in memory and nothing else forces the INSERT.
        property = propertyRepository.saveAndFlush(property);
        log.info("Property created: {} ({}) by {}",
            property.getId(), referenceCode(property), ownerEmail);
        return toDetailResponse(property, true);   // owner gets to see their own docs
    }

    // ─────────────────────────────────────────────
    // UPDATE LISTING
    // ─────────────────────────────────────────────

    @Transactional
    public PropertyDetailResponse update(UUID id, PropertyRequest req, String editorEmail) {
        Property property = propertyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));

        // Only owner or admin can update
        if (!property.getOwner().getEmail().equals(editorEmail)) {
            throw new UnauthorizedException("You can only edit your own listings");
        }

        Locality locality = localityRepository.findById(req.getLocalityId())
            .orElseThrow(() -> new ResourceNotFoundException("Locality", req.getLocalityId()));

        property.setLocality(locality);
        property.setTitle(req.getTitle());
        property.setDescription(req.getDescription());
        property.setListingType(req.getListingType());
        property.setPropertyType(req.getPropertyType());
        property.setPrice(req.getPrice());
        property.setPriceUnit(req.getPriceUnit());
        property.setPriceNegotiable(req.isPriceNegotiable());
        property.setSecurityDeposit(req.getSecurityDeposit());
        property.setBedrooms(req.getBedrooms() != null ? req.getBedrooms().shortValue() : null);
        property.setBathrooms(req.getBathrooms() != null ? req.getBathrooms().shortValue() : null);
        property.setBalconies(req.getBalconies() != null ? req.getBalconies().shortValue() : null);
        property.setTotalFloors(req.getTotalFloors() != null ? req.getTotalFloors().shortValue() : null);
        property.setFloorNumber(req.getFloorNumber() != null ? req.getFloorNumber().shortValue() : null);
        property.setAreaSqft(req.getAreaSqft());
        property.setCarpetAreaSqft(req.getCarpetAreaSqft());
        property.setFurnishing(req.getFurnishing());
        property.setFacing(req.getFacing());
        property.setAgeOfProperty(req.getAgeOfProperty() != null ? req.getAgeOfProperty().shortValue() : null);
        property.setAvailableFrom(req.getAvailableFrom());
        property.setAddressLine(req.getAddressLine());
        property.setLatitude(req.getLatitude());
        property.setLongitude(req.getLongitude());
        property.setParkingAvailable(req.isParkingAvailable());
        property.setParkingCount(req.getParkingCount() != null
            ? req.getParkingCount().shortValue() : null);
        property.setPossessionStatus(req.getPossessionStatus());
        property.setPreferredTenant(req.getPreferredTenant());

        // ── Phase B wizard fields ─────────────────
        if (req.getListedBy() != null) property.setListedBy(req.getListedBy());
        property.setPlotLengthFt(req.getPlotLengthFt());
        property.setPlotBreadthFt(req.getPlotBreadthFt());
        property.setPlotAreaCents(req.getPlotAreaCents());
        property.setRoadWidthFt(req.getRoadWidthFt());
        property.setBoundaryWall(req.getBoundaryWall());
        property.setCornerPlot(req.getCornerPlot());
        property.setApprovalAuthority(req.getApprovalAuthority());
        property.setOwnershipType(req.getOwnershipType());
        property.setSoilType(req.getSoilType());
        property.setWaterSource(req.getWaterSource());
        property.setHasWell(req.getHasWell());
        property.setElectricService(req.getElectricService());
        property.setCropCurrentlyGrown(req.getCropCurrentlyGrown());
        property.setFenced(req.getFenced());
        property.setPromoterProjectName(req.getPromoterProjectName());
        property.setPromoterYearsExperience(req.getPromoterYearsExperience() != null
            ? req.getPromoterYearsExperience().shortValue() : null);
        property.setPromoterTotalProjects(req.getPromoterTotalProjects() != null
            ? req.getPromoterTotalProjects().shortValue() : null);
        property.setPromoterCitiesActive(req.getPromoterCitiesActive());
        property.setPromoterReraId(req.getPromoterReraId());

        if (req.getAmenityIds() != null) {
            property.setAmenities(new HashSet<>(amenityRepository.findByIdIn(req.getAmenityIds())));
        }

        // Re-submit for review when edited (prevents bypassing moderation)
        property.setStatus(Property.ListingStatus.PENDING_REVIEW);

        return toDetailResponse(propertyRepository.save(property), true);  // owner editing — show docs
    }

    // ─────────────────────────────────────────────
    // DELETE LISTING
    // ─────────────────────────────────────────────

    @Transactional
    public void delete(UUID id, String requestorEmail, boolean isAdmin) {
        Property property = propertyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));

        if (!isAdmin && !property.getOwner().getEmail().equals(requestorEmail)) {
            throw new UnauthorizedException("You can only delete your own listings");
        }

        imageUploadService.deleteAllPropertyImages(id);
        imageUploadService.deleteAllPropertyDocuments(id);
        propertyRepository.delete(property);
        log.info("Property deleted: {} by {}", id, requestorEmail);
    }

    // ─────────────────────────────────────────────
    // IMAGE UPLOAD
    // ─────────────────────────────────────────────

    @Transactional
    public ImageResponse uploadImage(UUID propertyId, MultipartFile file,
                                     boolean setPrimary, String ownerEmail) {
        // Row lock so two concurrent uploads can't both pass the cap check
        // or compute the same sortOrder (#43)
        Property property = propertyRepository.findByIdForUpdate(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));

        if (!property.getOwner().getEmail().equals(ownerEmail)) {
            throw new UnauthorizedException("You can only upload images to your own listings");
        }
        if (imageRepository.countByPropertyId(propertyId) >= MAX_IMAGES_PER_PROPERTY) {
            throw new BadRequestException(
                "Maximum " + MAX_IMAGES_PER_PROPERTY + " images allowed per property"
            );
        }

        String url = imageUploadService.uploadPropertyImage(file, propertyId);

        boolean isFirst = imageRepository.countByPropertyId(propertyId) == 0;
        if (setPrimary || isFirst) {
            imageRepository.clearPrimaryFlag(propertyId);
        }

        PropertyImage image = PropertyImage.builder()
            .property(property)
            .url(url)
            .isPrimary(setPrimary || isFirst)
            .sortOrder((int) imageRepository.countByPropertyId(propertyId))
            .build();

        image = imageRepository.save(image);
        return ImageResponse.builder()
            .id(image.getId())
            .url(image.getUrl())
            .isPrimary(image.isPrimary())
            .sortOrder(image.getSortOrder())
            .build();
    }

    // ─────────────────────────────────────────────
    // IMAGE DELETE
    // ─────────────────────────────────────────────

    @Transactional
    public void deleteImage(UUID propertyId, UUID imageId, String ownerEmail) {
        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));

        if (!property.getOwner().getEmail().equals(ownerEmail)) {
            throw new UnauthorizedException("You can only delete images from your own listings");
        }

        PropertyImage image = imageRepository.findById(imageId)
            .orElseThrow(() -> new ResourceNotFoundException("Image", imageId));

        // Guard against IDOR: the image must belong to the property in the path,
        // otherwise an owner of one listing could delete another listing's image
        // by pairing their propertyId with someone else's imageId.
        if (!image.getProperty().getId().equals(propertyId)) {
            throw new ResourceNotFoundException("Image", imageId);
        }

        imageUploadService.deleteImage(image.getUrl());
        imageRepository.delete(image);
    }

    // ─────────────────────────────────────────────
    // DOCUMENT UPLOAD (FMB / EC / Patta / Approval letter)
    // ─────────────────────────────────────────────

    @Transactional
    public DocumentResponse uploadDocument(UUID propertyId, MultipartFile file,
                                           PropertyDocument.DocType docType,
                                           String label, String ownerEmail) {
        // Row lock — same #43 race as uploadImage, on the 8-document cap
        Property property = propertyRepository.findByIdForUpdate(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));

        if (!property.getOwner().getEmail().equals(ownerEmail)) {
            throw new UnauthorizedException("You can only upload documents to your own listings");
        }
        if (documentRepository.findByPropertyId(propertyId).size() >= MAX_DOCUMENTS_PER_PROPERTY) {
            throw new BadRequestException(
                "Maximum " + MAX_DOCUMENTS_PER_PROPERTY + " documents allowed per property"
            );
        }

        String url = imageUploadService.uploadPropertyDocument(file, propertyId);

        PropertyDocument doc = PropertyDocument.builder()
            .property(property)
            .docType(docType)
            .url(url)
            .label(label)
            .build();

        doc = documentRepository.save(doc);
        return DocumentResponse.builder()
            .id(doc.getId())
            .docType(doc.getDocType().name())
            .url(doc.getUrl())
            .label(doc.getLabel())
            .build();
    }

    /**
     * Admin-only: generate a short-lived signed download URL for a property document.
     * Used by the admin verification UI so doc URLs aren't exposed directly to the public.
     */
    @Transactional(readOnly = true)
    public DocumentDownloadResponse getDocumentDownloadUrl(UUID propertyId, UUID documentId) {
        PropertyDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        if (!doc.getProperty().getId().equals(propertyId)) {
            throw new ResourceNotFoundException("Document", documentId);
        }

        String signedUrl = imageUploadService.presignDownloadUrl(doc.getUrl());
        int    ttl       = imageUploadService.presignedDownloadTtlSeconds();

        return DocumentDownloadResponse.builder()
            .url(signedUrl)
            .expiresIn(ttl)
            .docType(doc.getDocType().name())
            .build();
    }

    @Transactional
    public void deleteDocument(UUID propertyId, UUID documentId, String ownerEmail) {
        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));

        if (!property.getOwner().getEmail().equals(ownerEmail)) {
            throw new UnauthorizedException("You can only delete documents from your own listings");
        }

        PropertyDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        // Guard against IDOR: the document must belong to the property in the path
        // (same flaw class as deleteImage — see getDocumentDownloadUrl for the pattern).
        if (!doc.getProperty().getId().equals(propertyId)) {
            throw new ResourceNotFoundException("Document", documentId);
        }

        imageUploadService.deleteDocument(doc.getUrl());
        documentRepository.delete(doc);
    }

    // ─────────────────────────────────────────────
    // OWNER: MY LISTINGS
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PropertyCardResponse> getMyListings(String ownerEmail, Pageable pageable) {
        User owner = userRepository.findByEmail(ownerEmail)
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        return propertyRepository
            .findByOwnerIdOrderByCreatedAtDesc(owner.getId(), pageable)
            .map(this::toCardResponse);
    }

    // ─────────────────────────────────────────────
    // ADMIN: PENDING LISTINGS
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PropertyCardResponse> getPendingListings(Pageable pageable) {
        return propertyRepository
            .findByStatusOrderByCreatedAtAsc(Property.ListingStatus.PENDING_REVIEW, pageable)
            .map(this::toCardResponse);
    }

    @Transactional(readOnly = true)
    public Page<PropertyCardResponse> getAllListings(Property.ListingStatus status, String q, Pageable pageable) {
        if (q != null && !q.isBlank()) {
            return propertyRepository.findAll(PropertySpecification.adminFilter(status, q), pageable)
                .map(this::toCardResponse);
        }
        // No text search → keep the original queries (status path sorts oldest-first
        // on purpose: it doubles as the review queue).
        if (status != null) {
            return propertyRepository.findByStatusOrderByCreatedAtAsc(status, pageable)
                .map(this::toCardResponse);
        }
        return propertyRepository.findAll(pageable).map(this::toCardResponse);
    }

    // ─────────────────────────────────────────────
    // ADMIN: APPROVE / REJECT / FEATURE
    // ─────────────────────────────────────────────

    @Transactional
    public PropertyDetailResponse updateStatus(UUID id, StatusUpdateRequest req) {
        Property property = propertyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));

        if (req.getStatus() == Property.ListingStatus.REJECTED
                && (req.getRejectionReason() == null || req.getRejectionReason().isBlank())) {
            throw new BadRequestException("Rejection reason is required");
        }

        // Enforce the "verified listings" claim: a listing can only go ACTIVE
        // (publicly visible) with at least one verification document on file.
        if (req.getStatus() == Property.ListingStatus.ACTIVE
                && !documentRepository.existsByPropertyId(id)) {
            throw new BadRequestException(
                "Cannot approve a listing with no verification documents");
        }

        property.setStatus(req.getStatus());
        if (req.getStatus() == Property.ListingStatus.ACTIVE) {
            property.setVerified(true);   // document-backed + admin-approved
        }
        if (req.getRejectionReason() != null) {
            property.setRejectionReason(req.getRejectionReason());
        }

        property = propertyRepository.save(property);
        log.info("Property {} status updated to {} by admin", id, req.getStatus());
        return toDetailResponse(property, true);   // admin action — show docs
    }

    @Transactional
    public PropertyDetailResponse toggleFeatured(UUID id) {
        Property property = propertyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));
        property.setFeatured(!property.isFeatured());
        return toDetailResponse(propertyRepository.save(property), true);   // admin action — show docs
    }

    // ─────────────────────────────────────────────
    // Mappers: Entity → DTO
    // ─────────────────────────────────────────────

    public PropertyCardResponse toCardResponse(Property p) {
        String primaryImageUrl = p.getImages().stream()
            .filter(PropertyImage::isPrimary)
            .map(PropertyImage::getUrl)
            .findFirst()
            .orElse(p.getImages().isEmpty() ? null : p.getImages().get(0).getUrl());

        return PropertyCardResponse.builder()
            .id(p.getId())
            .title(p.getTitle())
            .listingType(p.getListingType().name())
            .propertyType(p.getPropertyType().name())
            .price(p.getPrice())
            .priceUnit(p.getPriceUnit().name())
            .bedrooms(p.getBedrooms() != null ? (int) p.getBedrooms() : null)
            .bathrooms(p.getBathrooms() != null ? (int) p.getBathrooms() : null)
            .areaSqft(p.getAreaSqft())
            .furnishing(p.getFurnishing().name())
            .localityName(p.getLocality().getName())
            .cityName(p.getLocality().getCity().getName())
            .latitude(p.getLatitude())
            .longitude(p.getLongitude())
            .status(p.getStatus().name())
            .isFeatured(p.isFeatured())
            .isVerified(p.isVerified())
            .primaryImageUrl(primaryImageUrl)
            .viewsCount(p.getViewsCount())
            .createdAt(p.getCreatedAt())
            .build();
    }

    /**
     * Human-readable listing reference buyers quote on a call — "PF000100001".
     * Derived from the ref_seq column rather than stored, so there is exactly one
     * definition of the format. Null only for an entity that has not been flushed
     * yet (the DB default assigns ref_seq on insert).
     */
    private String referenceCode(Property p) {
        return p.getRefSeq() != null ? String.format("PF%09d", p.getRefSeq()) : null;
    }

    private PropertyDetailResponse toDetailResponse(Property p, boolean includeDocuments) {
        List<ImageResponse> images = p.getImages().stream()
            .map(img -> ImageResponse.builder()
                .id(img.getId())
                .url(img.getUrl())
                .isPrimary(img.isPrimary())
                .sortOrder(img.getSortOrder())
                .build())
            .toList();

        // Verification documents are PII-sensitive (survey numbers, EC entries, patta records).
        // The storage URL and the document id are owner/admin only — the public detail
        // endpoint gets a summary projection that has no url field to leak in the first place.
        // Buyers still see WHICH approvals exist, which is what the detail page renders.
        List<PropertyDocumentView> documents = documentRepository.findByPropertyId(p.getId()).stream()
            .map(d -> includeDocuments
                ? (PropertyDocumentView) DocumentResponse.builder()
                    .id(d.getId())
                    .docType(d.getDocType().name())
                    .url(d.getUrl())
                    .label(d.getLabel())
                    .build()
                : (PropertyDocumentView) DocumentSummaryResponse.builder()
                    .docType(d.getDocType().name())
                    .label(d.getLabel())
                    .build())
            .toList();

        List<AmenityResponse> amenities = p.getAmenities().stream()
            .map(a -> AmenityResponse.builder()
                .id(a.getId())
                .name(a.getName())
                .category(a.getCategory())
                .iconKey(a.getIconKey())
                .build())
            .toList();

        OwnerInfo ownerInfo = OwnerInfo.builder()
            .id(p.getOwner().getId())
            .name(p.getOwner().getName())
            .phone(p.getOwner().getPhone())
            .profilePhotoUrl(p.getOwner().getProfilePhotoUrl())
            .role(p.getOwner().getRole().name())
            .isEmailVerified(p.getOwner().isVerified())
            .memberSince(p.getOwner().getCreatedAt())
            .build();

        return PropertyDetailResponse.builder()
            .id(p.getId())
            .title(p.getTitle())
            .description(p.getDescription())
            .listingType(p.getListingType().name())
            .propertyType(p.getPropertyType().name())
            .status(p.getStatus().name())
            .rejectionReason(p.getRejectionReason())
            .price(p.getPrice())
            .priceUnit(p.getPriceUnit().name())
            .priceNegotiable(p.isPriceNegotiable())
            .securityDeposit(p.getSecurityDeposit())
            .bedrooms(p.getBedrooms() != null ? (int) p.getBedrooms() : null)
            .bathrooms(p.getBathrooms() != null ? (int) p.getBathrooms() : null)
            .balconies(p.getBalconies() != null ? (int) p.getBalconies() : null)
            .totalFloors(p.getTotalFloors() != null ? (int) p.getTotalFloors() : null)
            .floorNumber(p.getFloorNumber() != null ? (int) p.getFloorNumber() : null)
            .areaSqft(p.getAreaSqft())
            .carpetAreaSqft(p.getCarpetAreaSqft())
            .furnishing(p.getFurnishing().name())
            .facing(p.getFacing())
            .ageOfProperty(p.getAgeOfProperty() != null ? (int) p.getAgeOfProperty() : null)
            .availableFrom(p.getAvailableFrom())
            .parkingAvailable(p.isParkingAvailable())
            .parkingCount(p.getParkingCount() != null ? (int) p.getParkingCount() : null)
            .possessionStatus(p.getPossessionStatus() != null ? p.getPossessionStatus().name() : null)
            .referenceCode(referenceCode(p))
            .nearby(nearbyPlaceService.forLocality(
                p.getLocality().getId(), p.getLocality().getCity().getId()))
            .preferredTenant(p.getPreferredTenant() != null ? p.getPreferredTenant().name() : null)
            .addressLine(p.getAddressLine())
            .latitude(p.getLatitude())
            .longitude(p.getLongitude())
            .localityName(p.getLocality().getName())
            .localitySlug(p.getLocality().getSlug())
            .cityName(p.getLocality().getCity().getName())
            .citySlug(p.getLocality().getCity().getSlug())
            .isFeatured(p.isFeatured())
            .isVerified(p.isVerified())
            .viewsCount(p.getViewsCount())
            .inquiryCount(p.getInquiryCount())
            .images(images)
            .amenities(amenities)
            .owner(ownerInfo)
            .createdAt(p.getCreatedAt())
            .expiresAt(p.getExpiresAt())
            .listedBy(p.getListedBy() != null ? p.getListedBy().name() : null)
            .plotLengthFt(p.getPlotLengthFt())
            .plotBreadthFt(p.getPlotBreadthFt())
            .plotAreaCents(p.getPlotAreaCents())
            .roadWidthFt(p.getRoadWidthFt())
            .boundaryWall(p.getBoundaryWall())
            .cornerPlot(p.getCornerPlot())
            .approvalAuthority(p.getApprovalAuthority() != null ? p.getApprovalAuthority().name() : null)
            .ownershipType(p.getOwnershipType() != null ? p.getOwnershipType().name() : null)
            .soilType(p.getSoilType() != null ? p.getSoilType().name() : null)
            .waterSource(p.getWaterSource() != null ? p.getWaterSource().name() : null)
            .hasWell(p.getHasWell())
            .electricService(p.getElectricService() != null ? p.getElectricService().name() : null)
            .cropCurrentlyGrown(p.getCropCurrentlyGrown())
            .fenced(p.getFenced())
            .promoterProjectName(p.getPromoterProjectName())
            .promoterYearsExperience(p.getPromoterYearsExperience() != null ? (int) p.getPromoterYearsExperience() : null)
            .promoterTotalProjects(p.getPromoterTotalProjects() != null ? (int) p.getPromoterTotalProjects() : null)
            .promoterCitiesActive(p.getPromoterCitiesActive())
            .promoterReraId(p.getPromoterReraId())
            .documents(documents)
            .build();
    }

    /** Admin preview — any status, no view increment. */
    @Transactional(readOnly = true)
    public PropertyDetailResponse getByIdForAdmin(UUID id) {
        Property property = propertyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));
        return toDetailResponse(property, true);   // admin needs the docs to verify
    }

    @Transactional(readOnly = true)
    public PropertyDetailResponse getByIdForOwner(UUID id, String email) {
        Property property = propertyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));
        if (!property.getOwner().getEmail().equals(email)) {
            throw new UnauthorizedException("You don't have permission to view this listing");
        }
        return toDetailResponse(property, true);   // owner sees their own docs
    }

    /** Validates a property is active without incrementing view count. */
    public void validateActiveProperty(UUID id) {
        Property p = propertyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));
        if (p.getStatus() != Property.ListingStatus.ACTIVE) {
            throw new ResourceNotFoundException("Property", id);
        }
    }

    // ─────────────────────────────────────────────
    // INQUIRY
    // ─────────────────────────────────────────────

    @Transactional
    public void handleInquiry(UUID propertyId, String message,
                              String inquirerName, String inquirerPhone) {
        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));
        if (property.getStatus() != Property.ListingStatus.ACTIVE) {
            throw new ResourceNotFoundException("Property", propertyId);
        }

        propertyRepository.incrementInquiryCount(propertyId);

        // Notify owner by email (async — does not block the response)
        try {
            emailService.sendInquiryNotification(
                property.getOwner().getEmail(),
                property.getOwner().getName(),
                property.getTitle(),
                inquirerName != null ? inquirerName : "Anonymous",
                inquirerPhone != null ? inquirerPhone : "Not provided"
            );
        } catch (Exception e) {
            log.warn("Could not send inquiry notification for property {}: {}", propertyId, e.getMessage());
        }
    }

    private Property findActiveProperty(UUID id) {
        Property p = propertyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property", id));
        if (p.getStatus() != Property.ListingStatus.ACTIVE) {
            throw new ResourceNotFoundException("Property", id);
        }
        return p;
    }
}
