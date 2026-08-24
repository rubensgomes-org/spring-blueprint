# syntax=docker/dockerfile:1
#
# Multi-stage build for the spring-blueprint Spring Boot application.
#
#   builder   -- compiles and verifies with Gradle, produces the boot jar
#   extractor -- explodes the layered jar into its four cache layers
#   runtime   -- minimal JRE image running as a non-root user
#
# Build (BuildKit required for the secret mounts):
#
#   docker build \
#     --secret id=github_user,env=GITHUB_USER \
#     --secret id=github_token,env=GITHUB_TOKEN \
#     -t spring-blueprint:local .
#
# Or simply "docker compose build", which wires the same secrets.
#
# https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html


# ---------------------------------------------------------------------
# --------------- >>> Stage 1: builder <<< ----------------------------
# NOTE: this base is chosen deliberately, not incidentally. The build
# pins "vendor = JvmVendorSpec.MICROSOFT" and "languageVersion = 25" in
# app/build.gradle.kts. Gradle always considers the JVM running Gradle as
# a toolchain candidate, and this image reports java.vendor "Microsoft"
# at 25.0.4.1, so the spec is satisfied by the JVM already present and
# the foojay resolver never fires. A Temurin, Corretto or gradle:*
# builder would instead download an entire second ~200 MB Microsoft JDK
# on every cold build. Keep this base in step with the vendor pinned in
# the build script -- if one changes, the other must change with it.
#
# Unlike the Amazon Linux *minimal* images, this Ubuntu-based one already
# ships "find" and "xargs", so no extra package install is needed. The
# Gradle wrapper hard-requires xargs and aborts with "xargs is not
# available" before doing anything else; the jar-selection step below
# uses find. Do not switch to a slimmer base without re-checking both.
# ---------------------------------------------------------------------
FROM mcr.microsoft.com/openjdk/jdk:25-ubuntu AS builder

WORKDIR /build

# Build definition first, sources second. A source-only edit then leaves
# the Gradle-distribution and dependency-resolution layers cached at the
# BuildKit layer level, which -- unlike a cache mount -- survives
# --cache-from in CI.
#
# All three lock files are mandatory: dependency locking runs in
# LockMode.STRICT for both the project and the buildscript classpath, so
# a missing lock file fails the build rather than resolving freely.
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
#   docker build --build-arg GRADLE_BUILD_ARGS="-x check" ...
# Defaults to empty so the image can only be built from code that passes
# verification. CI must never set this.
#
# "-x check" alone prunes the whole verification subgraph: test,
# jacocoTestReport, jacocoTestCoverageVerification and spotlessCheck are
# reachable only through check.
ARG GRADLE_BUILD_ARGS=""

# The credentials are a command prefix, not ENV instructions, so they
# exist only in this one process. They never enter a layer or
# "docker history". Command substitution also strips the trailing newline
# that most secret managers append.
#
# GITHUB_USER/GITHUB_TOKEN are mandatory, not optional: settings.gradle.kts
# resolves the "com.rubensgomes:gradle-catalog" version catalog from GitHub
# Packages, and every plugin alias comes through that catalog, so a cold
# Docker cache resolves nothing without them. Missing credentials produce
# only a warning, then a confusing HTTP 401.
#
# sharing=locked on the cache mount is required: GRADLE_USER_HOME holds
# the modules-2 file locks and the build cache, so concurrent unlocked
# builds would corrupt it.
#
# auto-download=false makes a toolchain mismatch fail in seconds with
# "No matching toolchain" instead of silently pulling a second JDK, should
# anyone later change the base image above.
RUN --mount=type=secret,id=github_user \
    --mount=type=secret,id=github_token \
    --mount=type=cache,target=/root/.gradle,sharing=locked \
    GITHUB_USER="$(cat /run/secrets/github_user)" \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    ./gradlew --no-daemon --console=plain \
      -Porg.gradle.java.installations.auto-download=false \
      :app:bootJar ${GRADLE_BUILD_ARGS}

# The archive base name comes from the artifactId property, not the "app"
# project directory, and the release plugin bumps the version -- so glob
# rather than hardcode. The exclusions guard against -plain/-sources/
# -javadoc jars appearing if the build graph ever widens.
RUN set -eu; \
    jar="$(find app/build/libs -name 'spring-blueprint-*.jar' \
             ! -name '*-plain.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar')"; \
    [ -f "$jar" ] || { echo "no boot jar found in app/build/libs" >&2; exit 1; }; \
    cp "$jar" /build/application.jar


# ---------------------------------------------------------------------
# --------------- >>> Stage 2: extractor <<< --------------------------
# NOTE: Spring Boot 4 REMOVED the "layertools" jarmode -- the boot jar
# bundles spring-boot-jarmode-tools, and "-Djarmode=layertools" fails
# with "Unsupported jarmode". The "tools ... extract" form below is the
# Boot 3.3+ replacement.
#
# NOTE: --destination must not already exist or be non-empty;
# ExtractCommand refuses otherwise. Do not pre-create it.
#
# Its only input is the jar, so this stage re-runs only when the jar
# bytes change, and it runs on the small JRE rather than the JDK.
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS extractor

WORKDIR /extract
COPY --from=builder /build/application.jar ./application.jar
RUN java -Djarmode=tools -jar application.jar \
      extract --layers --launcher --destination /extract/layers


# ---------------------------------------------------------------------
# --------------- >>> Stage 3: runtime <<< ----------------------------
# NOTE: the alpine variant is chosen for the HEALTHCHECK. busybox
# provides wget at zero extra size, whereas the Ubuntu-based Temurin JRE
# images ship no wget and would need an apt-get layer for curl. There is
# no JVM-only alternative: a JRE cannot run a single-file source-launch
# health probe because it has no compiler.
# ---------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

ARG APP_VERSION="unknown"
LABEL org.opencontainers.image.title="spring-blueprint" \
      org.opencontainers.image.description="Blueprint Spring Boot Java project" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.source="https://github.com/rubensgomes-org/spring-blueprint" \
      org.opencontainers.image.licenses="Apache-2.0" \
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

# Exec form, so no shell is interposed and the JVM is PID 1. That is what
# lets "docker stop" deliver SIGTERM straight to the JVM and trigger
# Spring's graceful shutdown (server.shutdown=graceful,
# spring.lifecycle.timeout-per-shutdown-phase=5s). Wrapping this in
# "sh -c" to expand a $JAVA_OPTS variable would make sh PID 1, and sh does
# not forward SIGTERM -- graceful shutdown would silently degrade to a
# 10-second SIGKILL. JDK_JAVA_OPTIONS above provides that configurability
# without a shell.
#
# No -jar or -cp: with neither set the JVM defaults the classpath to ".",
# and WORKDIR is the merged exploded jar where JarLauncher lives.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
