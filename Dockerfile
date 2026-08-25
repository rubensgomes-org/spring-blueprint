# syntax=docker/dockerfile:1
#
# Multi-stage build for the spring-blueprint Spring Boot application.
#
#   builder   -- compiles and verifies with Gradle, produces the boot jar
#   extractor -- explodes the layered jar into its four cache layers
#   runtime   -- minimal JRE image running as a non-root user
#
# Build -- both variables must be exported first:
#
#   export GITHUB_USER=... GITHUB_TOKEN=...
#   docker build --build-arg GITHUB_USER --build-arg GITHUB_TOKEN \
#     -t spring-blueprint:local .
#
# Or simply "docker compose build", which forwards the same two variables.

# ---------------------------------------------------------------------
# --------------- >>> Stage 1: builder <<< ----------------------------
# NOTE: this base is chosen deliberately, not incidentally. The build
# pins "vendor = JvmVendorSpec.MICROSOFT" and "languageVersion = 25" in
# app/build.gradle.kts.
# ---------------------------------------------------------------------
FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu AS builder

WORKDIR /build

# Build definition first, sources second. A source-only edit then leaves
# the Gradle-distribution and dependency-resolution layers cached at the
# BuildKit layer level, which -- unlike a cache mount -- survives
# --cache-from in CI.
COPY gradlew                        ./
COPY gradle/                        ./gradle/
COPY settings.gradle.kts            ./
COPY gradle.properties              ./
COPY settings-gradle.lockfile       ./
COPY .editorconfig                  ./
COPY app/build.gradle.kts           ./app/
COPY app/gradle.properties          ./app/
COPY app/gradle.lockfile            ./app/
COPY app/buildscript-gradle.lockfile ./app/

# Both main and test sources: bootJar depends on check, which runs the
# full JUnit suite, spotless and a 90% line/branch JaCoCo gate.
COPY app/src/ ./app/src/

# Escape hatch for iterating on this Dockerfile:
#
#   docker build --build-arg GRADLE_BUILD_ARGS="-x check" ...
#
# "-x check" alone prunes the whole verification subgraph: test,
# jacocoTestReport, jacocoTestCoverageVerification and spotlessCheck are
# reachable only through check. Defaults to empty so the image can only be
# built from code that passes verification. CI must never set this.
ARG GRADLE_BUILD_ARGS=""

# GITHUB_USER/GITHUB_TOKEN are mandatory, not optional: settings.gradle.kts
# resolves the "com.rubensgomes:gradle-catalog" version catalog from GitHub
# Packages, and every plugin alias comes through that catalog, so a cold
# Docker cache resolves nothing without them.
#
# Callers pass them WITHOUT a value ("--build-arg GITHUB_USER"), so Docker
# reads each from the ambient environment and the token never appears in the
# process argv where "ps" could read it.
#
# auto-download=false makes a toolchain mismatch fail in seconds with "No
# matching toolchain" rather than silently pulling a second JDK.
ARG GITHUB_USER
ARG GITHUB_TOKEN
RUN set -eu; \
    if [ -z "${GITHUB_USER:-}" ] || [ -z "${GITHUB_TOKEN:-}" ]; then \
      echo "GITHUB_USER and GITHUB_TOKEN build args are required." >&2; \
      echo "This stage resolves com.rubensgomes:gradle-catalog from GitHub Packages." >&2; \
      exit 1; \
    fi; \
    ./gradlew --no-daemon --console=plain \
      -Porg.gradle.java.installations.auto-download=false \
      :app:bootJar ${GRADLE_BUILD_ARGS}

RUN set -eu; \
    jar="$(find app/build/libs -name 'spring-blueprint-*.jar' \
             ! -name '*-plain.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar')"; \
    [ -f "$jar" ] || { echo "no boot jar found in app/build/libs" >&2; exit 1; }; \
    cp "$jar" /build/application.jar


# ---------------------------------------------------------------------
# --------------- >>> Stage 2: extractor <<< --------------------------
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS extractor

WORKDIR /extract
COPY --from=builder /build/application.jar ./application.jar
RUN java -Djarmode=tools -jar application.jar \
      extract --layers --launcher --destination /extract/layers


# ---------------------------------------------------------------------
# --------------- >>> Stage 3: runtime <<< ----------------------------
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

ARG APP_VERSION="unknown"
LABEL org.opencontainers.image.title="spring-blueprint" \
      org.opencontainers.image.description="Blueprint Spring Boot Java project" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.source="https://github.com/rubensgomes-org/spring-blueprint" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.vendor="Rubens Gomes"

# A real account rather than a bare numeric USER, so getpwuid() lookups
# from the JVM resolve.
RUN addgroup -S -g 1001 spring \
 && adduser  -S -u 1001 -G spring -h /app -s /sbin/nologin spring

WORKDIR /app

# Ordered least-churn first. "dependencies" is ~23 MB and changes only
# when app/gradle.lockfile does; "application" is a few hundred KB and
# changes every commit. Boot writes constant 1980 timestamps into the
# extracted files, so the dependencies layer is byte-identical between
# builds and registries genuinely deduplicate it.
#
# "snapshot-dependencies" is empty for this project (all dependencies are
# release versions) but the directory IS created -- ExtractCommand makes
# one per layer listed in layers.idx -- so this COPY is safe.
COPY --from=extractor --chown=spring:spring /extract/layers/dependencies/          ./
COPY --from=extractor --chown=spring:spring /extract/layers/spring-boot-loader/    ./
COPY --from=extractor --chown=spring:spring /extract/layers/snapshot-dependencies/ ./
COPY --from=extractor --chown=spring:spring /extract/layers/application/           ./

# Baked so a bare "docker run" is not silent: the default profile pins
# logging.level.root to "error". See application-docker.yml.
#
# NOTE: MaxRAMPercentage is only meaningful when the container has a
# memory limit. Without one it computes 75% of total HOST RAM. This flag
# and the compose memory limit are a package deal.
#
# NOTE: setting JDK_JAVA_OPTIONS from compose REPLACES this value rather
# than appending to it.
ENV SPRING_PROFILES_ACTIVE="docker" \
    JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"

USER 1001:1001
EXPOSE 8080
STOPSIGNAL SIGTERM

# 127.0.0.1 rather than localhost: localhost may resolve to ::1 first and
# produce a false unhealthy. start-period is ~3.5x the ~5.6s measured
# startup, since a CPU-limited container with a cold page cache boots
# slower than a laptop. Failures inside that window do not count toward
# retries.
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
