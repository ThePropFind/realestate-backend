package com.realestate.service;

import com.realestate.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * LOCAL storage implementation — active in dev profile only.
 * Saves uploaded property images to local disk and returns a public URL.
 * Files are stored at: {upload-dir}/properties/{propertyId}/{uuid}.{ext}
 * Served by Spring at:  GET /uploads/properties/{propertyId}/{filename}
 *
 * In prod, S3StorageService takes over instead.
 */
@Service
@Profile("!prod")
@Slf4j
public class ImageUploadService implements StorageService {

    private static final long         MAX_FILE_SIZE   = 10 * 1024 * 1024L; // 10 MB
    private static final long         MAX_DOC_SIZE    = 15 * 1024 * 1024L; // 15 MB for PDFs
    private static final long         MAX_VIDEO_SIZE  = 50 * 1024 * 1024L; // 50 MB — a ~60s phone walkthrough
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

    private final Path   uploadRoot;
    private final String baseUrl;

    public ImageUploadService(
            @Value("${app.storage.upload-dir:uploads}") String uploadDir,
            @Value("${app.storage.base-url:http://localhost:8080}") String baseUrl) {

        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath();
        this.baseUrl    = baseUrl;

        try {
            Files.createDirectories(this.uploadRoot);
            log.info("Image upload directory: {}", this.uploadRoot);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create upload directory: " + this.uploadRoot, e);
        }
    }

    // ─────────────────────────────────────────────
    // Upload a single image — returns its public URL
    // ─────────────────────────────────────────────

    public String uploadPropertyImage(MultipartFile file, UUID propertyId) {
        validateFile(file);

        String ext      = getExtension(file.getContentType());
        String filename = UUID.randomUUID() + "." + ext;
        Path   dir      = uploadRoot.resolve("properties").resolve(propertyId.toString());
        Path   dest     = dir.resolve(filename);

        try {
            Files.createDirectories(dir);
            file.transferTo(dest);
            String url = "%s/uploads/properties/%s/%s".formatted(baseUrl, propertyId, filename);
            log.info("Image saved: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Failed to save image for property {}: {}", propertyId, e.getMessage());
            throw new RuntimeException("Image upload failed. Please try again.", e);
        }
    }

    // ─────────────────────────────────────────────
    // Delete an image by its URL
    // ─────────────────────────────────────────────

    public void deleteImage(String imageUrl) {
        try {
            // URL: http://localhost:8080/uploads/properties/{id}/{file}
            String marker = "/uploads/";
            int idx = imageUrl.indexOf(marker);
            if (idx < 0) return;
            String relativePath = imageUrl.substring(idx + marker.length());
            Path target = uploadRoot.resolve(relativePath);
            Files.deleteIfExists(target);
            log.info("Image deleted: {}", target);
        } catch (Exception e) {
            log.warn("Could not delete image file for URL: {}", imageUrl);
        }
    }

    // ─────────────────────────────────────────────
    // Delete all images for a property
    // ─────────────────────────────────────────────

    public void deleteAllPropertyImages(UUID propertyId) {
        try {
            Path dir = uploadRoot.resolve("properties").resolve(propertyId.toString());
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                          .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
                }
            }
        } catch (Exception e) {
            log.warn("Could not clean images for property {}: {}", propertyId, e.getMessage());
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
        // Defense-in-depth: the declared content-type is client-controlled, so also
        // confirm the actual bytes are a real image.
        FileContentValidator.validateImage(file);
    }

    /**
     * Derive a safe extension from the validated MIME type rather than from the
     * original filename. This prevents a malicious upload like "shell.php" with
     * content-type "image/jpeg" from being stored with a .php extension, which
     * could enable code execution if the file server is ever misconfigured.
     */
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

    @Override
    public String uploadPropertyVideo(MultipartFile file, UUID propertyId) {
        validateVideo(file);

        String ext      = getExtension(file.getContentType());
        String filename = UUID.randomUUID() + "." + ext;
        Path   dir      = uploadRoot.resolve("videos").resolve(propertyId.toString());
        Path   dest     = dir.resolve(filename);

        try {
            Files.createDirectories(dir);
            file.transferTo(dest);
            String url = "%s/uploads/videos/%s/%s".formatted(baseUrl, propertyId, filename);
            log.info("Video saved: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Failed to save video for property {}: {}", propertyId, e.getMessage());
            throw new RuntimeException("Video upload failed. Please try again.", e);
        }
    }

    @Override
    public void deleteVideo(String videoUrl) {
        // Same on-disk layout as deleteImage — everything lives under /uploads/
        deleteImage(videoUrl);
    }

    @Override
    public void deleteAllPropertyVideos(UUID propertyId) {
        try {
            Path dir = uploadRoot.resolve("videos").resolve(propertyId.toString());
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                          .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
                }
            }
        } catch (Exception e) {
            log.warn("Could not clean videos for property {}: {}", propertyId, e.getMessage());
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

        String ext      = getExtension(file.getContentType());
        String filename = UUID.randomUUID() + "." + ext;
        Path   dir      = uploadRoot.resolve("documents").resolve(propertyId.toString());
        Path   dest     = dir.resolve(filename);

        try {
            Files.createDirectories(dir);
            file.transferTo(dest);
            String url = "%s/uploads/documents/%s/%s".formatted(baseUrl, propertyId, filename);
            log.info("Document saved: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Failed to save document for property {}: {}", propertyId, e.getMessage());
            throw new RuntimeException("Document upload failed. Please try again.", e);
        }
    }

    @Override
    public void deleteDocument(String docUrl) {
        // Same on-disk layout as deleteImage — both live under /uploads/
        deleteImage(docUrl);
    }

    @Override
    public void deleteAllPropertyDocuments(UUID propertyId) {
        try {
            Path dir = uploadRoot.resolve("documents").resolve(propertyId.toString());
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                          .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
                }
            }
        } catch (Exception e) {
            log.warn("Could not clean documents for property {}: {}", propertyId, e.getMessage());
        }
    }

    /** Dev: local filesystem is already served at /uploads/** — no signing needed. */
    @Override
    public String presignDownloadUrl(String storedUrl) {
        return storedUrl;
    }

    @Override
    public int presignedDownloadTtlSeconds() {
        return 300;
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
}
