package com.realestate.service;

import com.realestate.dto.property.PropertyDtos.PropertyRequest;
import com.realestate.entity.Property;
import com.realestate.entity.User;
import com.realestate.exception.BadRequestException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.exception.UnauthorizedException;
import com.realestate.dto.property.PropertyDtos.StatusUpdateRequest;
import com.realestate.repository.PropertyDocumentRepository;
import com.realestate.repository.PropertyImageRepository;
import com.realestate.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Ownership/limit guard tests for {@link PropertyService} (complements the IDOR tests).
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceGuardTest {

    @Mock private PropertyRepository         propertyRepository;
    @Mock private PropertyImageRepository    imageRepository;
    @Mock private PropertyDocumentRepository documentRepository;

    @InjectMocks private PropertyService propertyService;

    private Property ownedBy(UUID id, String ownerEmail, Property.ListingStatus status) {
        User owner = User.builder().id(UUID.randomUUID()).email(ownerEmail).build();
        return Property.builder().id(id).owner(owner).status(status).build();
    }

    @Test
    void update_byNonOwner_throwsUnauthorized() {
        UUID id = UUID.randomUUID();
        when(propertyRepository.findById(id))
            .thenReturn(Optional.of(ownedBy(id, "owner@x.in", Property.ListingStatus.ACTIVE)));

        assertThatThrownBy(() ->
            propertyService.update(id, new PropertyRequest(), "attacker@x.in"))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void uploadImage_atMaxCapacity_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        // uploadImage takes the FOR UPDATE lock (#43), not the plain findById
        when(propertyRepository.findByIdForUpdate(id))
            .thenReturn(Optional.of(ownedBy(id, "owner@x.in", Property.ListingStatus.ACTIVE)));
        when(imageRepository.countByPropertyId(id)).thenReturn(20L); // MAX_IMAGES_PER_PROPERTY

        MultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() ->
            propertyService.uploadImage(id, file, false, "owner@x.in"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateStatus_approveWithNoDocuments_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        when(propertyRepository.findById(id))
            .thenReturn(Optional.of(ownedBy(id, "owner@x.in", Property.ListingStatus.PENDING_REVIEW)));
        when(documentRepository.existsByPropertyId(id)).thenReturn(false);

        StatusUpdateRequest req = new StatusUpdateRequest();
        req.setStatus(Property.ListingStatus.ACTIVE);

        assertThatThrownBy(() -> propertyService.updateStatus(id, req))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("verification documents");
    }

    @Test
    void handleInquiry_onInactiveProperty_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(propertyRepository.findById(id))
            .thenReturn(Optional.of(ownedBy(id, "owner@x.in", Property.ListingStatus.PENDING_REVIEW)));

        assertThatThrownBy(() ->
            propertyService.handleInquiry(id, "hi", "Buyer", "9876543210"))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
