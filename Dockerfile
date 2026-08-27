# syntax=docker/dockerfile:1
#
# Multi-stage Dockerfile for the spring-blueprint Spring Boot application.
#
#   This Dockerfile is not building the Spring Boot "uber or fat" jar.
#   The Spring Boot multi-layered package uber jar
#   "spring-blueprint-spring-boot.jar" file should be created from the
#   underlying gradle build.  Ant it is assumed to be available in
#   the gradle target build/libs folder. If not, you MUST pass the actual
#   file path using this Dockerfile ARG JAR_FILE argument.
#
#   extractor -- explodes the layered jar into its cache layers
#   runtime   -- minimal JRE image running as a non-root user
#
# Application Version.
#
#  The software built version must be passed in as CLI argument using
#  the ARG APP_VERSION configured in this Dockerfile.
#
# Build example:
#
#   docker build --build-arg APP_VERSION=1.0.0 -t spring-blueprint:1.0.0 .
#
# Or simply "docker compose build", which forwards the same variable.

# ---------------------------------------------------------------------
# --------------- >>> Stage 1: builder <<< ----------------------------
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS builder

WORKDIR /builder

# By default the JAR_FILE points to the gradle built
# spring-blueprint-spring-boot.jar Spring Boot uber file in the project
# app/build/libs folder. The "spring-blueprint-spring-boot.jar" is assumed
# to be a copy of the Spring Boot multi-layered jar file created during
# the "./gradlew build".
ARG JAR_FILE=app/build/libs/spring-blueprint-spring-boot.jar

# Copy the Spring Boot jar file to the working directory and rename
# it to application.jar
COPY ${JAR_FILE} application.jar

# Extract the S;ring Boot multi-layer jar file using an efficient layout
RUN java -Djarmode=tools -jar application.jar \
    extract --layers --destination extracted

# ---------------------------------------------------------------------
# --------------- >>> Stage 2: runtime <<< ----------------------------
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

# You MUST pass the APP_VERSION as an argument in the CLI command.
ARG APP_VERSION

RUN set -eu; \
    if [ -z "${APP_VERSION:-}" ]; then \
      echo "APP_VERSION must be provided." >&2; \
      exit 1; \
    fi;

LABEL org.opencontainers.image.title="spring-blueprint" \
      org.opencontainers.image.description="Blueprint Spring Boot Java project" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.source="https://github.com/rubensgomes-org/spring-blueprint" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.vendor="Rubens Gomes"

# Create a real account rather than a bare numeric USER, so getpwuid() lookups
# from the JVM resolve.
RUN addgroup -S -g 1001 spring \
 && adduser  -S -u 1001 -G spring -h /app -s /sbin/nologin spring

WORKDIR /app

# The builder stage extracts the directories that are needed later.
# Each of the COPY commands relates to the layers extracted by the jarmode.
#
# Ordered least-churn first. "dependencies" is ~23 MB and changes only
# when app/gradle.lockfile does; "application" is a few hundred KB and
# changes every commit. Boot writes constant 1980 timestamps into the
# extracted files, so the dependencies layer is byte-identical between
# builds and registries genuinely deduplicate it.
#
# "snapshot-dependencies" is empty for this project (all dependencies are
# release versions) but the directory IS created -- ExtractCommand makes
# one per layer listed in layers.idx -- so this COPY is safe.
COPY --from=builder --chown=spring:spring /builder/extracted/dependencies/          ./
COPY --from=builder --chown=spring:spring /builder/extracted/spring-boot-loader/    ./
COPY --from=builder --chown=spring:spring /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /builder/extracted/application/           ./

# The default Spring Boot profile below is being set to docker.  However,
# container runtime settings typically have higher precedence than the
# value baked into the image.  For example in the code below:
#
#   docker run \
#     -e SPRING_PROFILES_ACTIVE=prod \
#     myapp:1.0.0
#
# OR
#
#   env:
#    - name: SPRING_PROFILES_ACTIVE
#      value: prod
#
# would override the image default Sprint Boot Profile to prod.
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

# Execute the AOT cache training run
RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar application.jar

# Healthcheck
# 127.0.0.1 rather than localhost: localhost may resolve to ::1 first and
# produce a false unhealthy. start-period is ~3.5x the ~5.6s measured
# startup, since a CPU-limited container with a cold page cache boots
# slower than a laptop. Failures inside that window do not count toward
# retries.
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

# Start the application jar with AOT cache enabled - this is not the
# uber jar used by the builder. This jar only contains application code
# and references to the extracted jar files.
# This layout is efficient to start up and AOT cache friendly
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "application.jar"]
