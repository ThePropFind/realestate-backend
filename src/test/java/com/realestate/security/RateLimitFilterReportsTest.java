package com.realestate.security;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that POST /properties/{id}/reports is metered (B6).
 *
 * Reporting is public — no account, no token. Without a bucket, one script could
 * bury a competitor's listing under a thousand flags, or bury the moderation queue
 * under a thousand rows. It shares the 5/hr inquiry bucket rather than getting its
 * own: same shape of abuse, and a shared bucket also stops a caller alternating
 * between the two endpoints to double their budget.
 */
class RateLimitFilterReportsTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    private Bucket pickBucket(String method, String path, String ip) throws Exception {
        Method m = RateLimitFilter.class.getDeclaredMethod(
            "pickBucket", String.class, String.class, String.class);
        m.setAccessible(true);
        return (Bucket) m.invoke(filter, method, path, ip);
    }

    private static final String PATH = "/properties/1f2e3d4c-0000-0000-0000-000000000001/reports";

    @Test
    void sixthReportInAnHourFromTheSameIpIsRefused() throws Exception {
        for (int i = 1; i <= 5; i++) {
            assertThat(pickBucket("POST", PATH, "203.0.113.9").tryConsume(1))
                .as("report %d of 5 should be allowed", i)
                .isTrue();
        }
        assertThat(pickBucket("POST", PATH, "203.0.113.9").tryConsume(1))
            .as("the 6th report in the hour should be refused")
            .isFalse();
    }

    @Test
    void reportsShareTheInquiryBucketSoAlternatingDoesNotDoubleTheBudget() throws Exception {
        String ip = "203.0.113.10";
        String inquiries = "/properties/1f2e3d4c-0000-0000-0000-000000000001/inquiries";

        for (int i = 0; i < 5; i++) {
            assertThat(pickBucket("POST", i % 2 == 0 ? PATH : inquiries, ip).tryConsume(1)).isTrue();
        }
        assertThat(pickBucket("POST", inquiries, ip).tryConsume(1)).isFalse();
        assertThat(pickBucket("POST", PATH, ip).tryConsume(1)).isFalse();
    }

    @Test
    void aDifferentIpGetsItsOwnBudget() throws Exception {
        for (int i = 0; i < 5; i++) pickBucket("POST", PATH, "203.0.113.11").tryConsume(1);

        assertThat(pickBucket("POST", PATH, "198.51.100.4").tryConsume(1)).isTrue();
    }

    @Test
    void readsAreNotMetered() throws Exception {
        assertThat(pickBucket("GET", PATH, "203.0.113.12")).isNull();
    }
}
