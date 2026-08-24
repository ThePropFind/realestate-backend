package com.realestate.service;

import com.realestate.config.AppProperties;
import com.realestate.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AWS S3 storage implementation — active in prod profile only.
 *
 * Images are stored at key: properties/{propertyId}/{uuid}.{ext}
 * Returned URL: https://{bucket}.s3.{region}.amazonaws.com/properties/{propertyId}/{uuid}.{ext}
 *
 * Bucket must have public read enabled (see AWS setup guide in CLAUDE.md).
 */
@Service
@Profile("prod")
@Slf4j
public class S3StorageService implements StorageService {

    private static final long         MAX_FILE_SIZE   = 10 * 1024 * 1024L;
    private static final long         MAX_DOC_SIZE    = 15 * 1024 * 1024L;
    private static final long         MAX_VIDEO_SIZE  = 50 * 1024 * 1024L; // a ~60s phone walkthrough
    private static final List<String> ALLOWED_VIDEO_TYPES = List.of(
        "video/mp4", "video/quicktime"
    );
    private static final List<String> ALLOWED_TYPES   = List.of(
        "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final List<String> ALLOWED_DOC_TYPES = List.of(
        "application/pdf",
        "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private static final int PRESIGNED_DOWNLOAD_TTL_SECONDS = 300; // 5 minutes

    private final S3Client    s3Client;
    private final S3Presigner s3Presigner;
    private final String      bucket;
    private final String      imageBaseUrl;   // root of public image URLs
    private final String      docsBucket;     // PRIVATE bucket — documents, presigned GET only
    private final String      docsBaseUrl;    // stored-URL root for documents (auth-required, not public)

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner, AppProperties appProperties) {
        this.s3Client    = s3Client;
        this.s3Presigner = s3Presigner;
        AppProperties.Aws aws = appProperties.getAws();
        this.bucket = aws.getS3().getBucketName();
        String configuredDocsBucket = aws.getS3().getDocsBucketName();
        this.docsBucket = (configuredDocsBucket == null || configuredDocsBucket.isBlank())
            ? this.bucket : configuredDocsBucket;

        String endpoint  = aws.getEndpoint();
        String publicUrl = aws.getPublicUrl();

        if (publicUrl != null && !publicUrl.isBlank()) {
            // R2 pub URL: bucket is implicit — path is just the key, no bucket prefix
            // e.g. https://pub-xxx.r2.dev/properties/{id}/{file}  (NOT /bucket/properties/...)
            this.imageBaseUrl = publicUrl.stripTrailing();
            log.info("S3StorageService active (R2/CDN) — api: {}, public: {}, bucket: {}", endpoint, publicUrl, bucket);
        } else if (endpoint != null && !endpoint.isBlank()) {
            // MinIO self-hosted — same host serves both API and public reads
            this.imageBaseUrl = endpoint + "/" + bucket;
            log.info("S3StorageService active (MinIO) — endpoint: {}, bucket: {}", endpoint, bucket);
        } else {
            // AWS S3 virtual-hosted URL
            this.imageBaseUrl = "https://%s.s3.%s.amazonaws.com".formatted(bucket, aws.getRegion());
            log.info("S3StorageService active (AWS S3) — bucket: {}, region: {}", bucket, aws.getRegion());
        }

        // Document URLs are never public: prefer the S3-API endpoint (path-style,
        // SigV4-gated on R2/MinIO) over any public CDN URL. Stored URLs are only
        // parsed back by this class (presign/delete) — clients get presigned GETs.
        if (endpoint != null && !endpoint.isBlank()) {
            this.docsBaseUrl = endpoint + "/" + docsBucket;
        } else if (publicUrl == null || publicUrl.isBlank()) {
            this.docsBaseUrl = "https://%s.s3.%s.amazonaws.com".formatted(docsBucket, aws.getRegion());
        } else {
            this.docsBaseUrl = imageBaseUrl; // public-URL-only setup: no separate private path exists
        }
        log.info("Documents bucket: {} ({})", docsBucket,
            docsBucket.equals(bucket) ? "shared with images — set MINIO_DOCS_BUCKET for a private bucket" : "private");
    }

    @Override
    public String uploadPropertyImage(MultipartFile file, UUID propertyId) {
        validateFile(file);

        String ext      = getExtension(file.getContentType());
        String key      = "properties/%s/%s.%s".formatted(propertyId, UUID.randomUUID(), ext);

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

            s3Client.putObject(putReq, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String url = imageBaseUrl + "/" + key;
            log.info("Image uploaded: {}", url);
            return url;

        } catch (IOException e) {
            log.error("S3 upload failed for property {}: {}", propertyId, e.getMessage());
            throw new RuntimeException("Image upload failed. Please try again.", e);
        }
    }

    @Override
    public void deleteImage(String imageUrl) {
        String key = extractKeyFromUrl(imageUrl);
        if (key == null) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            log.info("S3 image deleted: {}", key);
        } catch (Exception e) {
            log.warn("Could not delete S3 object for URL: {}", imageUrl);
        }
    }

    @Override
    public void deleteAllPropertyImages(UUID propertyId) {
        String prefix = "properties/%s/".formatted(propertyId);
        try {
            var listReq = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
            var objects = s3Client.listObjectsV2(listReq).contents();
            if (objects.isEmpty()) return;

            List<ObjectIdentifier> toDelete = objects.stream()
                .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                .collect(Collectors.toList());

            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(d -> d.objects(toDelete))
                .build());

            log.info("Deleted {} S3 images for property {}", toDelete.size(), propertyId);
        } catch (Exception e) {
            log.warn("Could not delete S3 images for property {}: {}", propertyId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new BadRequestException("No file provided");
        if (file.getSize() > MAX_FILE_SIZE)
            throw new BadRequestException("File size exceeds 10 MB limit");
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw new BadRequestException("Invalid file type. Only JPEG, PNG and WebP are allowed.");
        // Defense-in-depth: confirm the actual bytes are a real image, not just the
        // client-declared content-type.
        FileContentValidator.validateImage(file);
    }

    private String getExtension(String contentType) {
        if (contentType == null) return "jpg";
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png"               -> "png";
            case "image/webp"              -> "webp";
            case "application/pdf"         -> "pdf";
            case "video/mp4"               -> "mp4";
            case "video/quicktime"         -> "mov";
            default                        -> "jpg";
        };
    }

    // ─────────────────────────────────────────────
    // Video (walkthrough)
    // ─────────────────────────────────────────────

    /**
     * Walkthroughs go in the PUBLIC image bucket, not the private documents one:
     * a buyer's phone streams the file straight off the CDN URL, and a presigned
     * GET would expire mid-playback.
     */
    @Override
    public String uploadPropertyVideo(MultipartFile file, UUID propertyId) {
        validateVideo(file);

        String ext = getExtension(file.getContentType());
        String key = "videos/%s/%s.%s".formatted(propertyId, UUID.randomUUID(), ext);

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

            s3Client.putObject(putReq, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String url = imageBaseUrl + "/" + key;
            log.info("Video uploaded: {}", url);
            return url;

        } catch (IOException e) {
            log.error("S3 video upload failed for property {}: {}", propertyId, e.getMessage());
            throw new RuntimeException("Video upload failed. Please try again.", e);
        }
    }

    @Override
    public void deleteVideo(String videoUrl) {
        String key = extractKeyFromUrl(videoUrl);
        if (key == null) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            log.info("S3 video deleted: {}", key);
        } catch (Exception e) {
            log.warn("Could not delete S3 video for URL: {}", videoUrl);
        }
    }

    @Override
    public void deleteAllPropertyVideos(UUID propertyId) {
        String prefix = "videos/%s/".formatted(propertyId);
        try {
            var listReq = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
            var objects = s3Client.listObjectsV2(listReq).contents();
            if (objects.isEmpty()) return;

            List<ObjectIdentifier> toDelete = objects.stream()
                .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                .collect(Collectors.toList());

            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(d -> d.objects(toDelete))
                .build());

            log.info("Deleted {} S3 videos for property {}", toDelete.size(), propertyId);
        } catch (Exception e) {
            log.warn("Could not delete S3 videos for property {}: {}", propertyId, e.getMessage());
        }
    }

    private void validateVideo(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new BadRequestException("No file provided");
        if (file.getSize() > MAX_VIDEO_SIZE)
            throw new BadRequestException("Video size exceeds 50 MB limit");
        if (!ALLOWED_VIDEO_TYPES.contains(file.getContentType()))
            throw new BadRequestException("Invalid video type. Only MP4 and MOV are allowed.");
        FileContentValidator.validateVideo(file);
    }

    // ─────────────────────────────────────────────
    // Documents (PDF or image)
    // ─────────────────────────────────────────────

    @Override
    public String uploadPropertyDocument(MultipartFile file, UUID propertyId) {
        validateDocument(file);

        String ext = getExtension(file.getContentType());
        String key = "documents/%s/%s.%s".formatted(propertyId, UUID.randomUUID(), ext);

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(docsBucket)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

            s3Client.putObject(putReq, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String url = docsBaseUrl + "/" + key;
            log.info("Document uploaded to {}: {}", docsBucket, key);
            return url;
        } catch (IOException e) {
            log.error("S3 document upload failed for property {}: {}", propertyId, e.getMessage());
            throw new RuntimeException("Document upload failed. Please try again.", e);
        }
    }

    @Override
    public void deleteDocument(String docUrl) {
        String[] loc = resolveBucketAndKey(docUrl);
        if (loc == null) {
            log.warn("Could not resolve S3 location for document URL: {}", docUrl);
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(loc[0]).key(loc[1]).build());
            log.info("S3 document deleted: {}/{}", loc[0], loc[1]);
        } catch (Exception e) {
            log.warn("Could not delete S3 document: {}", docUrl);
        }
    }

    @Override
    public void deleteAllPropertyDocuments(UUID propertyId) {
        String prefix = "documents/%s/".formatted(propertyId);
        deleteByPrefix(docsBucket, prefix, propertyId);
        // Legacy: documents uploaded before the private bucket existed live in
        // the images bucket under the same prefix — sweep those too.
        if (!docsBucket.equals(bucket)) deleteByPrefix(bucket, prefix, propertyId);
    }

    private void deleteByPrefix(String inBucket, String prefix, UUID propertyId) {
        try {
            var listReq = ListObjectsV2Request.builder().bucket(inBucket).prefix(prefix).build();
            var objects = s3Client.listObjectsV2(listReq).contents();
            if (objects.isEmpty()) return;

            List<ObjectIdentifier> toDelete = objects.stream()
                .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                .collect(Collectors.toList());

            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(inBucket)
                .delete(d -> d.objects(toDelete))
                .build());

            log.info("Deleted {} S3 documents in {} for property {}", toDelete.size(), inBucket, propertyId);
        } catch (Exception e) {
            log.warn("Could not delete S3 documents in {} for property {}: {}", inBucket, propertyId, e.getMessage());
        }
    }

    @Override
    public String presignDownloadUrl(String storedUrl) {
        String[] loc = resolveBucketAndKey(storedUrl);
        if (loc == null) {
            // Fall back to the stored URL — caller still gets *something* clickable.
            log.warn("Could not resolve S3 location from URL, returning raw URL: {}", storedUrl);
            return storedUrl;
        }
        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(loc[0])
                .key(loc[1])
                .build();

            GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGNED_DOWNLOAD_TTL_SECONDS))
                .getObjectRequest(getReq)
                .build();

            return s3Presigner.presignGetObject(presignReq).url().toString();
        } catch (Exception e) {
            log.error("Failed to presign GET for key {}: {}", loc[1], e.getMessage());
            return storedUrl;
        }
    }

    /**
     * Resolve a stored URL to {bucket, key}. New document URLs live under
     * docsBaseUrl (private bucket); legacy URLs (public CDN / MinIO / AWS
     * virtual-hosted) resolve to the images bucket so pre-migration rows
     * keep presigning and deleting correctly.
     */
    private String[] resolveBucketAndKey(String url) {
        if (url == null) return null;
        String docsPrefix = docsBaseUrl + "/";
        if (url.startsWith(docsPrefix)) return new String[]{ docsBucket, url.substring(docsPrefix.length()) };
        int idx = url.indexOf(".amazonaws.com/");
        if (idx >= 0) return new String[]{ bucket, url.substring(idx + ".amazonaws.com/".length()) };
        String legacyPrefix = imageBaseUrl + "/";
        if (url.startsWith(legacyPrefix)) return new String[]{ bucket, url.substring(legacyPrefix.length()) };
        return null;
    }

    @Override
    public int presignedDownloadTtlSeconds() {
        return PRESIGNED_DOWNLOAD_TTL_SECONDS;
    }

    private void validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new BadRequestException("No file provided");
        if (file.getSize() > MAX_DOC_SIZE)
            throw new BadRequestException("File size exceeds 15 MB limit");
        if (!ALLOWED_DOC_TYPES.contains(file.getContentType()))
            throw new BadRequestException("Invalid document type. Only PDF, JPEG, PNG and WebP are allowed.");
        FileContentValidator.validateDocument(file);
    }

    /**
     * Extract the S3 object key from a public URL.
     * AWS format:   https://{bucket}.s3.{region}.amazonaws.com/{key}
     * MinIO format: http://{host}:{port}/{bucket}/{key}
     */
    private String extractKeyFromUrl(String url) {
        if (url == null) return null;
        // AWS virtual-hosted URL
        int idx = url.indexOf(".amazonaws.com/");
        if (idx >= 0) return url.substring(idx + ".amazonaws.com/".length());
        // MinIO path-style URL: strip base prefix "{endpoint}/{bucket}/"
        String prefix = imageBaseUrl + "/";
        if (url.startsWith(prefix)) return url.substring(prefix.length());
        return null;
    }
}
