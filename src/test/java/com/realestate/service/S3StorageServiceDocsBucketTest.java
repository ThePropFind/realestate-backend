package com.realestate.service;

import com.realestate.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Documents must land in the PRIVATE docs bucket (presigned GET only), while
 * images stay on the public bucket — and legacy document URLs written before
 * the private bucket existed must still presign/delete against the old bucket.
 */
@ExtendWith(MockitoExtension.class)
class S3StorageServiceDocsBucketTest {

    private static final String ENDPOINT    = "https://acc.r2.cloudflarestorage.com";
    private static final String PUBLIC_URL  = "https://pub-x.r2.dev";
    private static final String IMAGES      = "property-images";
    private static final String DOCS        = "property-documents";

    private static final byte[] PDF_BYTES = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;

    private S3StorageService service(String docsBucketName) {
        AppProperties props = new AppProperties();
        props.getAws().setAccessKey("k");
        props.getAws().setSecretKey("s");
        props.getAws().setRegion("us-east-1");
        props.getAws().setEndpoint(ENDPOINT);
        props.getAws().setPublicUrl(PUBLIC_URL);
        props.getAws().getS3().setBucketName(IMAGES);
        props.getAws().getS3().setDocsBucketName(docsBucketName);
        return new S3StorageService(s3Client, s3Presigner, props);
    }

    @Test
    void documentUpload_goesToPrivateDocsBucket_urlIsNotPublic() {
        var svc  = service(DOCS);
        var file = new MockMultipartFile("file", "patta.pdf", "application/pdf", PDF_BYTES);

        String url = svc.uploadPropertyDocument(file, UUID.randomUUID());

        var cap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(cap.capture(), any(RequestBody.class));
        assertThat(cap.getValue().bucket()).isEqualTo(DOCS);
        assertThat(url).startsWith(ENDPOINT + "/" + DOCS + "/documents/");
        assertThat(url).doesNotContain(PUBLIC_URL); // never a world-readable URL
    }

    @Test
    void imageUpload_staysOnPublicBucket() {
        var svc  = service(DOCS);
        var file = new MockMultipartFile("file", "photo.png", "image/png", PNG_BYTES);

        String url = svc.uploadPropertyImage(file, UUID.randomUUID());

        var cap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(cap.capture(), any(RequestBody.class));
        assertThat(cap.getValue().bucket()).isEqualTo(IMAGES);
        assertThat(url).startsWith(PUBLIC_URL + "/properties/");
    }

    @Test
    void presign_routesNewDocsUrlToDocsBucket_andLegacyUrlToImagesBucket() throws Exception {
        var svc = service(DOCS);
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://signed.example/x").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
        var cap = ArgumentCaptor.forClass(GetObjectPresignRequest.class);

        // New-style stored URL → private docs bucket
        String out = svc.presignDownloadUrl(ENDPOINT + "/" + DOCS + "/documents/p1/f.pdf");
        assertThat(out).isEqualTo("https://signed.example/x");

        // Legacy public URL (pre-migration row) → images bucket
        svc.presignDownloadUrl(PUBLIC_URL + "/documents/p1/old.pdf");

        verify(s3Presigner, org.mockito.Mockito.times(2)).presignGetObject(cap.capture());
        assertThat(cap.getAllValues().get(0).getObjectRequest().bucket()).isEqualTo(DOCS);
        assertThat(cap.getAllValues().get(0).getObjectRequest().key()).isEqualTo("documents/p1/f.pdf");
        assertThat(cap.getAllValues().get(1).getObjectRequest().bucket()).isEqualTo(IMAGES);
        assertThat(cap.getAllValues().get(1).getObjectRequest().key()).isEqualTo("documents/p1/old.pdf");
    }

    @Test
    void deleteDocument_routesToCorrectBucket() {
        var svc = service(DOCS);
        var cap = ArgumentCaptor.forClass(DeleteObjectRequest.class);

        svc.deleteDocument(ENDPOINT + "/" + DOCS + "/documents/p1/f.pdf");
        svc.deleteDocument(PUBLIC_URL + "/documents/p1/old.pdf");

        verify(s3Client, org.mockito.Mockito.times(2)).deleteObject(cap.capture());
        assertThat(cap.getAllValues().get(0).bucket()).isEqualTo(DOCS);
        assertThat(cap.getAllValues().get(1).bucket()).isEqualTo(IMAGES);
    }

    @Test
    void blankDocsBucket_fallsBackToImagesBucket() {
        var svc  = service("");
        var file = new MockMultipartFile("file", "ec.pdf", "application/pdf", PDF_BYTES);

        svc.uploadPropertyDocument(file, UUID.randomUUID());

        var cap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(cap.capture(), any(RequestBody.class));
        assertThat(cap.getValue().bucket()).isEqualTo(IMAGES);
    }
}
