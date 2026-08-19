package com.realestate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realestate.dto.property.PropertyDtos.DocumentResponse;
import com.realestate.dto.property.PropertyDtos.DocumentSummaryResponse;
import com.realestate.dto.property.PropertyDtos.PropertyDetailResponse;
import com.realestate.entity.City;
import com.realestate.entity.Locality;
import com.realestate.entity.Property;
import com.realestate.entity.PropertyDocument;
import com.realestate.entity.User;
import com.realestate.repository.PropertyDocumentRepository;
import com.realestate.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins the two projections of the property detail response (B1).
 *
 * The public endpoint must never emit a verification-document storage URL or
 * document id — docs hold owner PII (survey numbers, EC entries, patta records)
 * and live in a private bucket read only through an admin presigned download.
 * That is enforced structurally: the public path builds
 * {@link DocumentSummaryResponse}, which has no url field to populate, rather
 * than nulling a field on the full {@link DocumentResponse}.
 *
 * Also pins the owner trust signals, which the mapper previously never read off
 * the User row at all.
 */
@ExtendWith(MockitoExtension.class)
class PropertyDetailProjectionTest {

    @Mock private PropertyRepository         propertyRepository;
    @Mock private PropertyDocumentRepository documentRepository;
    @Mock private NearbyPlaceService         nearbyPlaceService;

    @InjectMocks private PropertyService propertyService;

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .findAndRegisterModules();   // JavaTimeModule, for memberSince

    private static final String OWNER_EMAIL = "owner@propfind.in";
    private static final LocalDateTime JOINED = LocalDateTime.of(2021, 1, 14, 9, 30);

    private final UUID propertyId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    private Property listing(boolean ownerEmailVerified) {
        User owner = User.builder()
            .id(UUID.randomUUID())
            .name("Anitha R")
            .email(OWNER_EMAIL)
            .phone("9876543210")
            .role(User.Role.SELLER)
            .verified(ownerEmailVerified)
            .createdAt(JOINED)
            .build();

        City city = City.builder().id(UUID.randomUUID()).name("Coimbatore").slug("coimbatore").build();
        Locality locality = Locality.builder()
            .id(UUID.randomUUID()).name("Gandhipuram").slug("gandhipuram").city(city).build();

        return Property.builder()
            .id(propertyId)
            .owner(owner)
            .locality(locality)
            .title("3BHK Individual Villa")
            .listingType(Property.ListingType.SALE)
            .propertyType(Property.PropertyType.VILLA)
            .status(Property.ListingStatus.ACTIVE)
            .price(new BigDecimal("9000000"))
            .priceUnit(Property.PriceUnit.TOTAL)
            .furnishing(Property.FurnishingStatus.SEMI_FURNISHED)
            .areaSqft(new BigDecimal("1938"))
            .images(List.of())
            .amenities(Set.of())
            .build();
    }

    private PropertyDocument pattaDoc() {
        return PropertyDocument.builder()
            .id(documentId)
            .docType(PropertyDocument.DocType.PATTA)
            .url("https://private-bucket/documents/" + propertyId + "/secret.pdf")
            .label("Patta Document")
            .build();
    }

    // ── Public projection ────────────────────────────────────────

    @Test
    void publicDetail_returnsDocumentTypeAndLabelOnly() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(listing(true)));
        when(documentRepository.findByPropertyId(propertyId)).thenReturn(List.of(pattaDoc()));

        PropertyDetailResponse res = propertyService.getById(propertyId);

        assertThat(res.getDocuments()).hasSize(1);
        assertThat(res.getDocuments().get(0)).isInstanceOf(DocumentSummaryResponse.class);

        DocumentSummaryResponse doc = (DocumentSummaryResponse) res.getDocuments().get(0);
        assertThat(doc.getDocType()).isEqualTo("PATTA");
        assertThat(doc.getLabel()).isEqualTo("Patta Document");
    }

    @Test
    void publicDetail_serializesWithNoUrlAndNoDocumentIdKey() throws Exception {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(listing(true)));
        when(documentRepository.findByPropertyId(propertyId)).thenReturn(List.of(pattaDoc()));

        PropertyDetailResponse res = propertyService.getById(propertyId);
        String documentsJson = MAPPER.writeValueAsString(res.getDocuments());

        // The key must be absent entirely, not present-and-null: a serialized
        // "url": null still advertises the shape and invites a careless remap.
        assertThat(documentsJson).doesNotContain("url");
        assertThat(documentsJson).doesNotContain(documentId.toString());
        assertThat(documentsJson).doesNotContain("private-bucket");
        assertThat(documentsJson).contains("PATTA");
    }

    @Test
    void publicDetail_withNoDocuments_returnsEmptyList() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(listing(true)));
        when(documentRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        assertThat(propertyService.getById(propertyId).getDocuments()).isEmpty();
    }

    // ── Owner projection ─────────────────────────────────────────

    @Test
    void ownerDetail_stillReturnsTheFullDocumentWithUrl() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(listing(true)));
        when(documentRepository.findByPropertyId(propertyId)).thenReturn(List.of(pattaDoc()));

        PropertyDetailResponse res = propertyService.getByIdForOwner(propertyId, OWNER_EMAIL);

        assertThat(res.getDocuments()).hasSize(1);
        assertThat(res.getDocuments().get(0)).isInstanceOf(DocumentResponse.class);

        DocumentResponse doc = (DocumentResponse) res.getDocuments().get(0);
        assertThat(doc.getId()).isEqualTo(documentId);
        assertThat(doc.getUrl()).contains("private-bucket");
    }

    @Test
    void adminDetail_stillReturnsTheFullDocumentWithUrl() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(listing(true)));
        when(documentRepository.findByPropertyId(propertyId)).thenReturn(List.of(pattaDoc()));

        PropertyDetailResponse res = propertyService.getByIdForAdmin(propertyId);

        assertThat(res.getDocuments().get(0)).isInstanceOf(DocumentResponse.class);
        assertThat(((DocumentResponse) res.getDocuments().get(0)).getUrl()).isNotBlank();
    }

    // ── Owner trust signals ──────────────────────────────────────

    @Test
    void ownerInfo_carriesEmailVerifiedAndMemberSince() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(listing(true)));
        when(documentRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        PropertyDetailResponse res = propertyService.getById(propertyId);

        assertThat(res.getOwner().isEmailVerified()).isTrue();
        assertThat(res.getOwner().getMemberSince()).isEqualTo(JOINED);
        assertThat(res.getOwner().getName()).isEqualTo("Anitha R");
    }

    @Test
    void ownerInfo_unverifiedEmailIsReportedHonestly() {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(listing(false)));
        when(documentRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        assertThat(propertyService.getById(propertyId).getOwner().isEmailVerified()).isFalse();
    }

    @Test
    void ownerInfo_serializesEmailVerifiedUnderTheIsPrefixedName() throws Exception {
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(listing(true)));
        when(documentRepository.findByPropertyId(propertyId)).thenReturn(List.of());

        String json = MAPPER.writeValueAsString(propertyService.getById(propertyId).getOwner());

        // Clients type this field as isEmailVerified — Lombok's getter would
        // otherwise make Jackson emit "emailVerified" and silently break them.
        assertThat(json).contains("\"isEmailVerified\":true");
        assertThat(json).doesNotContain("\"emailVerified\"");
        // The dead fields are gone, not nulled.
        assertThat(json).doesNotContain("avgRating");
        assertThat(json).doesNotContain("agencyName");
    }
}
