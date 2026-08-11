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
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=60.0", \
  "-Dlogging.file.name=/app/logs/app.log", \
  "-jar", "app.jar"]
