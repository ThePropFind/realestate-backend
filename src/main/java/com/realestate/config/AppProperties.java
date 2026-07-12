package com.realestate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds all custom `app.*` properties from application.properties
 * into typed Java objects. Inject this class wherever you need
 * JWT secret, S3 config, mail config, etc.
 *
 * Usage:
 *   @Autowired
 *   private AppProperties appProperties;
 *   String secret = appProperties.getJwt().getSecret();
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Aws aws = new Aws();
    private final Mail mail = new Mail();
    private final Cors cors = new Cors();
    private final Pagination pagination = new Pagination();

    // ─────────────────────────────────────────────
    // JWT
    // ─────────────────────────────────────────────
    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenExpiryMs;
        private long refreshTokenExpiryMs;
        private String issuer;
    }

    // ─────────────────────────────────────────────
    // AWS S3 / MinIO
    // ─────────────────────────────────────────────
    @Data
    public static class Aws {
        private String accessKey;
        private String secretKey;
        private String region;
        /**
         * S3 API endpoint for upload/delete operations.
         * R2/MinIO: https://account.r2.cloudflarestorage.com or http://localhost:9000
         * Leave blank for real AWS S3.
         */
        private String endpoint;
        /**
         * Public base URL for serving images in the browser.
         * R2: https://pub-xxxxx.r2.dev
         * MinIO self-hosted: same as endpoint (e.g. http://server:9000)
         * Leave blank to derive from endpoint or AWS bucket URL.
         */
        private String publicUrl;
        private final S3 s3 = new S3();

        @Data
        public static class S3 {
            private String bucketName;
            /**
             * Separate PRIVATE bucket for verification documents (FMB/EC/patta).
             * No public access — reads go through presigned GETs only.
             * Blank/unset → falls back to bucketName (single-bucket dev setups).
             */
            private String docsBucketName;
        }
    }

    // ─────────────────────────────────────────────
    // Mail
    // ─────────────────────────────────────────────
    @Data
    public static class Mail {
        private String from;
        private String fromName;
        /**
         * SendGrid Web API key, used by SendGridEmailTransport (prod profile).
         * Sent over HTTPS (port 443) because Render blocks outbound SMTP ports.
         * Defaults to MAIL_PASSWORD so the existing SMTP key is reused.
         */
        private String apiKey;
    }

    // ─────────────────────────────────────────────
    // CORS
    // ─────────────────────────────────────────────
    @Data
    public static class Cors {
        /** Comma-separated origins e.g. https://yoursite.com */
        private String allowedOrigins;

        public String[] getAllowedOriginsArray() {
            if (allowedOrigins == null) return new String[]{};
            return java.util.Arrays.stream(allowedOrigins.split(","))
                .map(s -> s.trim().replaceAll("/+$", ""))
                .toArray(String[]::new);
        }
    }

    // ─────────────────────────────────────────────
    // Pagination
    // ─────────────────────────────────────────────
    @Data
    public static class Pagination {
        private int defaultPageSize = 20;
        private int maxPageSize = 100;
    }
}
