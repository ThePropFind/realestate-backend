package com.realestate.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the JVM tuning that keeps prod alive on a 512MB / ~0.1-CPU Render free instance.
 *
 * <p>Regression #58 killed the container twice with exit 137, for two different reasons
 * that look identical from the exit code alone:
 * <ol>
 *   <li>2026-08-11 — a real kernel OOM: the heap ceiling alone was 75% of RAM.</li>
 *   <li>2026-08-15 — <em>not</em> an OOM. Render logged "Port scan timeout reached" and
 *       then SIGKILLed. Spring binds the Tomcat connector at the end of context refresh,
 *       so a 4.5-minute boot never opened a port inside Render's 5-minute window.</li>
 * </ol>
 *
 * <p>Both fixes are pure configuration with no code path to cover, which is exactly how
 * the first one silently regressed into the second. These assertions pin them.
 *
 * <p>All of this is sized for the free tier — revisit on the Phase 5 paid instance.
 */
class ProdRuntimeTuningTest {

    private static Properties loadProdProperties() throws Exception {
        Properties props = new Properties();
        try (InputStream in = ProdRuntimeTuningTest.class
                .getClassLoader().getResourceAsStream("application-prod.properties")) {
            assertThat(in).as("application-prod.properties on the test classpath").isNotNull();
            props.load(in);
        }
        return props;
    }

    /** The Dockerfile is not on the classpath — read it from the module root. */
    private static String loadDockerfile() throws Exception {
        Path dockerfile = Path.of("Dockerfile");
        assertThat(dockerfile).as("Dockerfile at the module root").exists();
        return Files.readString(dockerfile, StandardCharsets.UTF_8);
    }

    @Test
    void heapLeavesRoomForTheJvmsNonHeapFootprint() throws Exception {
        // 50% of 512MB = 256MB heap. Anything higher stopped fitting alongside
        // metaspace + code cache + stacks once the Sentry SDK was enabled.
        assertThat(loadDockerfile()).contains("-XX:MaxRAMPercentage=50.0");
    }

    @Test
    void everyMajorMemoryRegionIsCapped() throws Exception {
        String dockerfile = loadDockerfile();

        assertThat(dockerfile)
            .as("metaspace must be bounded — it is unlimited by default")
            .contains("-XX:MaxMetaspaceSize=");
        assertThat(dockerfile)
            .as("code cache reserves 240MB by default")
            .contains("-XX:ReservedCodeCacheSize=");
        assertThat(dockerfile)
            .as("thread stacks default to 1MB each")
            .contains("-Xss");
    }

    @Test
    void startupIsTunedToBindThePortInsideRendersScanWindow() throws Exception {
        String dockerfile = loadDockerfile();

        // C2 compilation is the biggest CPU consumer during boot and buys nothing on a
        // container that gets killed before it ever warms up.
        assertThat(dockerfile)
            .as("JIT must stop at C1 while the instance is CPU-starved")
            .contains("-XX:TieredStopAtLevel=1");
        // G1's region tables and GC threads cost native memory and scheduler time that a
        // single-core instance gains nothing back from.
        assertThat(dockerfile).contains("-XX:+UseSerialGC");
    }

    @Test
    void tomcatThreadPoolIsBoundedWellBelowTheDefault() throws Exception {
        String maxThreads = loadProdProperties().getProperty("server.tomcat.threads.max");

        assertThat(maxThreads)
            .as("server.tomcat.threads.max must be set — Spring's default 200 reserves ~100MB of stack")
            .isNotNull();
        assertThat(Integer.parseInt(maxThreads))
            .as("must stay well under Spring's 200 default while the instance is 512MB")
            .isPositive()
            .isLessThanOrEqualTo(50);
    }
}
