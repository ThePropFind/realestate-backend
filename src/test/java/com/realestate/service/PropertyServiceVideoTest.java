package com.realestate.service;

import com.realestate.dto.property.PropertyDtos.VideoResponse;
import com.realestate.entity.Property;
import com.realestate.entity.User;
import com.realestate.exception.UnauthorizedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The walkthrough video (V19) is one column, not a table, so the whole surface is
 * "set it, replace it, clear it" — and each of those has a way to lose a file or
 * leak one. These pin the three that matter:
 *   - only the owner can touch it;
 *   - replacing deletes the OLD object, and only after the new one is stored;
 *   - clearing an already-empty video is a no-op, not a storage call.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceVideoTest {

    @Mock private PropertyRepository propertyRepository;
    @Mock private StorageService     imageUploadService;

    @InjectMocks private PropertyService propertyService;

    private static final String OWNER_EMAIL   = "owner@propfind.in";
    private static final String ATTACKER_MAIL = "attacker@propfind.in";

    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};

    private Property ownedProperty(UUID id, String videoUrl) {
        User owner = User.builder().id(UUID.randomUUID()).email(OWNER_EMAIL).build();
        return Property.builder().id(id).owner(owner).videoUrl(videoUrl).build();
    }

    private MultipartFile video() {
        return new MockMultipartFile("file", "walkthrough.mp4", "video/mp4", MP4);
    }

    // ── upload ───────────────────────────────────────────────────

    @Test
    void uploadVideo_storesUrlOnTheListing() {
        UUID id = UUID.randomUUID();
        Property property = ownedProperty(id, null);
        when(propertyRepository.findByIdForUpdate(id)).thenReturn(Optional.of(property));
        when(imageUploadService.uploadPropertyVideo(any(), any())).thenReturn("https://cdn/videos/a.mp4");

        VideoResponse response = propertyService.uploadVideo(id, video(), OWNER_EMAIL);

        assertThat(response.getVideoUrl()).isEqualTo("https://cdn/videos/a.mp4");
        assertThat(property.getVideoUrl()).isEqualTo("https://cdn/videos/a.mp4");
        verify(propertyRepository).save(property);
        // Nothing to clean up on a first upload.
        verify(imageUploadService, never()).deleteVideo(any());
    }

    @Test
    void uploadVideo_replacesAndDeletesThePreviousFile() {
        UUID id = UUID.randomUUID();
        Property property = ownedProperty(id, "https://cdn/videos/old.mp4");
        when(propertyRepository.findByIdForUpdate(id)).thenReturn(Optional.of(property));
        when(imageUploadService.uploadPropertyVideo(any(), any())).thenReturn("https://cdn/videos/new.mp4");

        propertyService.uploadVideo(id, video(), OWNER_EMAIL);

        assertThat(property.getVideoUrl()).isEqualTo("https://cdn/videos/new.mp4");
        verify(imageUploadService).deleteVideo("https://cdn/videos/old.mp4");
    }

    @Test
    void uploadVideo_rejectsANonOwner() {
        UUID id = UUID.randomUUID();
        when(propertyRepository.findByIdForUpdate(id)).thenReturn(Optional.of(ownedProperty(id, null)));

        MultipartFile file = video();
        assertThatThrownBy(() -> propertyService.uploadVideo(id, file, ATTACKER_MAIL))
            .isInstanceOf(UnauthorizedException.class);

        // The guard has to run BEFORE the upload, or an attacker can still fill the bucket.
        verify(imageUploadService, never()).uploadPropertyVideo(any(), any());
        verify(propertyRepository, never()).save(any());
    }

    // ── delete ───────────────────────────────────────────────────

    @Test
    void deleteVideo_clearsTheColumnAndTheObject() {
        UUID id = UUID.randomUUID();
        Property property = ownedProperty(id, "https://cdn/videos/a.mp4");
        when(propertyRepository.findById(id)).thenReturn(Optional.of(property));

        propertyService.deleteVideo(id, OWNER_EMAIL);

        assertThat(property.getVideoUrl()).isNull();
        verify(propertyRepository).save(property);
        verify(imageUploadService).deleteVideo("https://cdn/videos/a.mp4");
    }

    @Test
    void deleteVideo_onAListingWithoutOneIsANoOp() {
        UUID id = UUID.randomUUID();
        when(propertyRepository.findById(id)).thenReturn(Optional.of(ownedProperty(id, null)));

        propertyService.deleteVideo(id, OWNER_EMAIL);

        verify(imageUploadService, never()).deleteVideo(any());
        verify(propertyRepository, never()).save(any());
    }

    @Test
    void deleteVideo_rejectsANonOwner() {
        UUID id = UUID.randomUUID();
        when(propertyRepository.findById(id)).thenReturn(Optional.of(ownedProperty(id, "https://cdn/videos/a.mp4")));

        assertThatThrownBy(() -> propertyService.deleteVideo(id, ATTACKER_MAIL))
            .isInstanceOf(UnauthorizedException.class);

        verify(imageUploadService, never()).deleteVideo(any());
    }
}
