package com.realestate.controller;

import com.realestate.dto.property.PropertyDtos.*;
import com.realestate.dto.property.PropertySearchRequest;
import com.realestate.dto.report.ReportDtos.ReportListingRequest;
import com.realestate.dto.report.ReportDtos.ReportListingResponse;
import com.realestate.entity.PropertyDocument;
import com.realestate.service.PropertyReportService;
import com.realestate.service.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public and authenticated property endpoints.
 * Routes under /api/properties
 */
@RestController
@RequestMapping("/properties")
@RequiredArgsConstructor
@Tag(name = "Properties", description = "Property listings — browse, create, manage")
public class PropertyController {

    private final PropertyService       propertyService;
    private final PropertyReportService reportService;

    // ─────────────────────────────────────────────
    // PUBLIC endpoints (no JWT needed)
    // ─────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Search/browse properties with filters")
    public ResponseEntity<Page<PropertyCardResponse>> search(
            PropertySearchRequest searchRequest,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParts = sort.split(",");
        String   sortField = sortParts[0];
        Sort.Direction dir = sortParts.length > 1
            && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
            Sort.by(dir, sortField));

        return ResponseEntity.ok(propertyService.searchProperties(searchRequest, pageable));
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured properties for homepage")
    public ResponseEntity<List<PropertyCardResponse>> getFeatured() {
        return ResponseEntity.ok(propertyService.getFeatured());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full property detail (increments view count)")
    public ResponseEntity<PropertyDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(propertyService.getById(id));
    }

    @GetMapping("/{id}/my")
    @Operation(summary = "Get your own listing regardless of status (for edit page)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PropertyDetailResponse> getMyProperty(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(propertyService.getByIdForOwner(id, currentUser.getUsername()));
    }

    @GetMapping("/{id}/similar")
    @Operation(summary = "Get similar properties in the same area")
    public ResponseEntity<List<PropertyCardResponse>> getSimilar(@PathVariable UUID id) {
        return ResponseEntity.ok(propertyService.getSimilar(id));
    }

    // ─────────────────────────────────────────────
    // AUTHENTICATED endpoints (JWT required)
    // ─────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a new property listing",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PropertyDetailResponse> create(
            @Valid @RequestBody PropertyRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(propertyService.create(request, currentUser.getUsername()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update your listing (re-submits for review)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PropertyDetailResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PropertyRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        return ResponseEntity.ok(
            propertyService.update(id, request, currentUser.getUsername())
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete your listing",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails currentUser) {

        propertyService.delete(id, currentUser.getUsername(), false);
        return ResponseEntity.noContent().build();
    }

    // ── Inquiries (public — guests can also inquire) ──────────

    @Data
    public static class InquiryRequest {
        private String message;
        private String guestName;
        private String guestEmail;
        private String guestPhone;
    }

    @PostMapping("/{id}/inquiries")
    @Transactional
    @Operation(summary = "Send an inquiry to the property owner")
    public ResponseEntity<Map<String, String>> sendInquiry(
            @PathVariable UUID id,
            @RequestBody InquiryRequest request) {

        propertyService.handleInquiry(id, request.getMessage(),
            request.getGuestName(), request.getGuestPhone());

        return ResponseEntity.ok(Map.of("message", "Inquiry sent successfully."));
    }

    // ── Reporting ─────────────────────────────────

    @PostMapping("/{id}/reports")
    @Operation(summary = "Report a listing (logged-in or guest)")
    public ResponseEntity<ReportListingResponse> report(
            @PathVariable UUID id,
            @Valid @RequestBody ReportListingRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {

        String email = currentUser != null ? currentUser.getUsername() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(reportService.report(id, request, email));
    }

    // ── Image management ──────────────────────────

    @PostMapping("/{id}/images")
    @Operation(summary = "Upload an image for a property (max 20)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ImageResponse> uploadImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean setPrimary,
            @AuthenticationPrincipal UserDetails currentUser) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
            propertyService.uploadImage(id, file, setPrimary, currentUser.getUsername())
        );
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a property image",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID id,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal UserDetails currentUser) {

        propertyService.deleteImage(id, imageId, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    // ── Video (walkthrough) ───────────────────────

    @PostMapping("/{id}/video")
    @Operation(summary = "Upload or replace the walkthrough video for a property (MP4/MOV, max 50 MB)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<VideoResponse> uploadVideo(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails currentUser) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
            propertyService.uploadVideo(id, file, currentUser.getUsername())
        );
    }

    @DeleteMapping("/{id}/video")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove the walkthrough video from a property",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteVideo(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails currentUser) {

        propertyService.deleteVideo(id, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    // ── Documents (FMB / EC / Patta / Approval letter) ────────

    @PostMapping("/{id}/documents")
    @Operation(summary = "Upload a verification document (PDF or image) for a property",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") PropertyDocument.DocType docType,
            @RequestParam(value = "label", required = false) String label,
            @AuthenticationPrincipal UserDetails currentUser) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
            propertyService.uploadDocument(id, file, docType, label, currentUser.getUsername())
        );
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a property document",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @AuthenticationPrincipal UserDetails currentUser) {

        propertyService.deleteDocument(id, documentId, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    // ── Owner: My listings ────────────────────────

    @GetMapping("/my-listings")
    @Operation(summary = "Get all listings posted by the current user",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<PropertyCardResponse>> getMyListings(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails currentUser) {

        Pageable pageable = PageRequest.of(page, size,
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(
            propertyService.getMyListings(currentUser.getUsername(), pageable)
        );
    }
}
