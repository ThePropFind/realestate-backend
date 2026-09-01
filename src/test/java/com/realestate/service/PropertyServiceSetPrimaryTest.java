package com.realestate.service;

import com.realestate.dto.property.PropertyDtos.ImageResponse;
import com.realestate.entity.Property;
import com.realestate.entity.PropertyImage;
import com.realestate.entity.User;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.exception.UnauthorizedException;
import com.realestate.repository.PropertyImageRepository;
import com.realestate.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code PATCH /properties/{id}/images/{imageId}/primary} — regression #90.
 *
 * Before this endpoint the cover could only be chosen while uploading, so
 * re-covering an existing listing meant deleting the current cover first. The
 * guards mirror deleteImage's, because the attack surface is identical: an
 * owner pairing their own propertyId with a foreign imageId.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceSetPrimaryTest {

    @Mock private PropertyRepository      propertyRepository;
    @Mock private PropertyImageRepository imageRepository;

    @InjectMocks private PropertyService propertyService;

    private static final String OWNER_EMAIL = "owner@propfind.in";

    private Property ownedProperty(UUID id) {
        User owner = User.builder().id(UUID.randomUUID()).email(OWNER_EMAIL).build();
        return Property.builder().id(id).owner(owner).build();
    }

    private PropertyImage imageOn(Property property, boolean primary) {
        return PropertyImage.builder()
            .id(UUID.randomUUID())
            .property(property)
            .url("https://cdn.test/photo.jpg")
            .isPrimary(primary)
            .sortOrder(3)
            .build();
    }

    @Test
    void promotesTheImageAndClearsTheOldCover() {
        UUID propertyId = UUID.randomUUID();
        Property property = ownedProperty(propertyId);
        PropertyImage image = imageOn(property, false);

        when(propertyRepository.findByIdForUpdate(propertyId)).thenReturn(Optional.of(property));
        when(imageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(imageRepository.save(any(PropertyImage.class))).thenAnswer(inv -> inv.getArgument(0));

        ImageResponse res = propertyService.setPrimaryImage(propertyId, image.getId(), OWNER_EMAIL);

        // Clearing every sibling flag first is what stops a listing ending up
        // with two primaries; toCardResponse takes findFirst and would then
        // pick whichever row the driver happened to return first.
        verify(imageRepository).clearPrimaryFlag(propertyId);
        assertThat(res.isPrimary()).isTrue();
        assertThat(res.getSortOrder()).isEqualTo(3);
    }

    @Test
    void aNonOwnerCannotChangeTheCover() {
        UUID propertyId = UUID.randomUUID();
        when(propertyRepository.findByIdForUpdate(propertyId))
            .thenReturn(Optional.of(ownedProperty(propertyId)));

        assertThatThrownBy(() ->
            propertyService.setPrimaryImage(propertyId, UUID.randomUUID(), "attacker@propfind.in"))
            .isInstanceOf(UnauthorizedException.class);

        verify(imageRepository, never()).clearPrimaryFlag(any());
        verify(imageRepository, never()).save(any());
    }

    /** IDOR: my listing's id + someone else's image id must not resolve. */
    @Test
    void anImageBelongingToAnotherListingIs404() {
        UUID propertyId = UUID.randomUUID();
        Property mine = ownedProperty(propertyId);
        Property theirs = ownedProperty(UUID.randomUUID());
        PropertyImage foreign = imageOn(theirs, false);

        when(propertyRepository.findByIdForUpdate(propertyId)).thenReturn(Optional.of(mine));
        when(imageRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() ->
            propertyService.setPrimaryImage(propertyId, foreign.getId(), OWNER_EMAIL))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(imageRepository, never()).clearPrimaryFlag(any());
        verify(imageRepository, never()).save(any());
    }

    @Test
    void anUnknownPropertyIs404() {
        UUID propertyId = UUID.randomUUID();
        when(propertyRepository.findByIdForUpdate(propertyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            propertyService.setPrimaryImage(propertyId, UUID.randomUUID(), OWNER_EMAIL))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
