package com.realestate.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

/**
 * Abstraction over image storage backends.
 * Dev profile: LocalStorageService (saves to disk, served via /uploads/**)
 * Prod profile: S3StorageService (uploads to AWS S3, served via CloudFront or S3 URLs)
 */
public interface StorageService {

    /**
     * Upload a property image and return its publicly accessible URL.
     */
    String uploadPropertyImage(MultipartFile file, UUID propertyId);

    /**
     * Delete a single image by its URL.
     */
    void deleteImage(String imageUrl);

    /**
     * Delete all images for a property (called on property deletion).
     */
    void deleteAllPropertyImages(UUID propertyId);

    /**
     * Upload a property walkthrough video and return its publicly accessible URL.
     * Stored under videos/{propertyId}/{uuid}.{ext} in the same public bucket as
     * images — a walkthrough is marketing material, not a private document.
     */
    String uploadPropertyVideo(MultipartFile file, UUID propertyId);

    /** Delete a single video by its URL. */
    void deleteVideo(String videoUrl);

    /** Delete all videos for a property (called on property deletion). */
    void deleteAllPropertyVideos(UUID propertyId);

    /**
     * Upload a property verification document (PDF or image) and return its public URL.
     * Stored under documents/{propertyId}/{uuid}.{ext}.
     */
    String uploadPropertyDocument(MultipartFile file, UUID propertyId);

    /** Delete a single document by its URL. */
    void deleteDocument(String docUrl);

    /** Delete all documents for a property (called on property deletion). */
    void deleteAllPropertyDocuments(UUID propertyId);

    /**
     * Convert a stored document URL into a short-lived signed download URL.
     * Dev profile returns the URL unchanged (local filesystem is fine).
     * Prod profile returns an S3 presigned GET URL with a 5-minute TTL.
     */
    String presignDownloadUrl(String storedUrl);

    /** TTL (seconds) used for presigned download URLs. */
    int presignedDownloadTtlSeconds();
}
