# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Cache dependencies layer separately (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: Run ────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create dirs before switching to non-root user
RUN mkdir -p /app/uploads /app/logs

# Security: don't run as root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# MaxRAMPercentage caps the HEAP only — it must leave room for the JVM's
# non-heap footprint (metaspace ~100MB for Spring Boot, code cache, thread
# stacks, direct buffers ≈ 150-200MB total). At 75% of a 512MB Render free
# instance the heap ceiling alone is ~384MB, so 384+200 overcommits the
# container and the kernel SIGKILLs it (exit 137) once real usage approaches
# that ceiling. Latent since day one; surfaced 2026-08-11 when enabling the
# Sentry SDK added ~30-50MB and tipped it over — three OOM kills in 15 min
# (regression #58). 60% (~307MB heap) keeps total RSS under 512MB.
# Revisit when moving to a paid instance (ROADMAP Phase 5).
#
# 2026-08-15: a deploy died at exit 137 again, but NOT from a kernel OOM — Render
# logged "Port scan timeout reached" first, then SIGKILLed. Spring binds the Tomcat
# connector at the very END of context refresh, so Hikari (26s on a cold Neon
# resume), Flyway and Hibernate all have to finish before Render sees an open port.
# On a free instance (WEB_CONCURRENCY=1, ~0.1 CPU) that boot ran 4.5min against a
# 5min window. The fix is startup CPU, and memory only insofar as GC burns it:
#   TieredStopAtLevel=1  stops at C1 — JIT compilation is the single biggest CPU
#                        consumer during boot, and C2 buys nothing on a container
#                        that gets killed before it warms up. Costs peak
#                        throughput; irrelevant until the Phase 5 paid tier.
#   SerialGC             fewer GC threads to schedule on a fraction of a core.
# Each memory region also gets a ceiling now — capping the heap alone left the
# rest unbounded, so the total was never actually budgeted:
#   heap     256MB  (50% — this app serves 10 listings; it never needed 307)
#   metaspace 128MB (was UNBOUNDED; Spring Boot settles ~110MB)
#   code cache 64MB (default reserves 240MB)
#   stacks    ~25MB (50 Tomcat threads x 512k — see server.tomcat.threads.max
#                    in application-prod.properties; Spring's default is 200)
# SerialGC because G1's region tables and per-GC-thread structures cost real
# native memory that a single-core 512MB instance gains nothing back from.
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=50.0", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:ReservedCodeCacheSize=64m", \
  "-XX:+UseSerialGC", \
  "-XX:TieredStopAtLevel=1", \
  "-Xss512k", \
  "-Dlogging.file.name=/app/logs/app.log", \
  "-jar", "app.jar"]
