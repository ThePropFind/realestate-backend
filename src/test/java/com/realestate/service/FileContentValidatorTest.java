package com.realestate.service;

import com.realestate.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the #36 magic-byte validator. The key property: a non-image payload
 * sent with a forged image content-type is rejected by content, not trusted by header.
 */
class FileContentValidatorTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] PNG  = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] PDF  = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    // ISO base media: 4-byte box size, then the literal "ftyp", then the brand.
    private static final byte[] MP4  = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};
    private static final byte[] MOV  = {0, 0, 0, 0x14, 'f', 't', 'y', 'p', 'q', 't', ' ', ' '};

    private MockMultipartFile file(byte[] content, String declaredType) {
        return new MockMultipartFile("file", "upload", declaredType, content);
    }

    @Test
    void realImages_passImageValidation() {
        assertThatCode(() -> FileContentValidator.validateImage(file(JPEG, "image/jpeg"))).doesNotThrowAnyException();
        assertThatCode(() -> FileContentValidator.validateImage(file(PNG, "image/png"))).doesNotThrowAnyException();
        assertThatCode(() -> FileContentValidator.validateImage(file(WEBP, "image/webp"))).doesNotThrowAnyException();
    }

    @Test
    void pdfRejectedAsImage_butAcceptedAsDocument() {
        assertThatThrownBy(() -> FileContentValidator.validateImage(file(PDF, "image/jpeg")))
            .isInstanceOf(BadRequestException.class);
        assertThatCode(() -> FileContentValidator.validateDocument(file(PDF, "application/pdf")))
            .doesNotThrowAnyException();
    }

    @Test
    void forgedContentType_isRejectedByActualBytes() {
        // A text/script payload claiming to be a JPEG must be rejected on content.
        MockMultipartFile fake = file("<?php echo 'pwned'; ?>".getBytes(), "image/jpeg");
        assertThatThrownBy(() -> FileContentValidator.validateImage(fake))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> FileContentValidator.validateDocument(fake))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void realVideos_passVideoValidation() {
        assertThatCode(() -> FileContentValidator.validateVideo(file(MP4, "video/mp4"))).doesNotThrowAnyException();
        assertThatCode(() -> FileContentValidator.validateVideo(file(MOV, "video/quicktime"))).doesNotThrowAnyException();
    }

    @Test
    void video_isNotAnImageAndNotADocument() {
        // A 50 MB walkthrough must never slip through the photo or the EC slot.
        assertThatThrownBy(() -> FileContentValidator.validateImage(file(MP4, "image/jpeg")))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> FileContentValidator.validateDocument(file(MP4, "application/pdf")))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void nonVideoRejectedAsVideo() {
        assertThatThrownBy(() -> FileContentValidator.validateVideo(file(JPEG, "video/mp4")))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> FileContentValidator.validateVideo(file("not a video".getBytes(), "video/mp4")))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void emptyFile_isRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> FileContentValidator.validateImage(empty))
            .isInstanceOf(BadRequestException.class);
    }
}
