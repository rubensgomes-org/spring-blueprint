# syntax=docker/dockerfile:1
#
# Multi-stage build for the spring-blueprint Spring Boot application.
#
#   builder   -- compiles and verifies with Gradle, produces the boot jar
#   extractor -- explodes the layered jar into its four cache layers
#   runtime   -- minimal JRE image running as a non-root user
#
# Build (GITHUB_USER and GITHUB_TOKEN must be exported first -- the
# valueless "--build-arg" form makes Docker read them from the environment
# rather than the command line):
#
#   docker build \
#     --build-arg GITHUB_USER \
#     --build-arg GITHUB_TOKEN \
#     -t spring-blueprint:local .
#
# Or "docker compose build", or "./gradlew :app:dockerBuild", both of which
# wire the same two args. In CI the image is built server-side by
# "az acr build" -- see .github/workflows/build-deploy-image.yml.
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
# All four lock files are mandatory: dependency locking runs in
# LockMode.STRICT for both the project and the buildscript classpath, so
# a missing lock file fails the build rather than resolving freely.
#
# The ROOT "build.gradle.kts" and its lock file are equally mandatory,
# for a reason that is easy to miss. It applies spotless and sonarqube,
# which puts both on the root buildscript classpath, and ":app" INHERITS
# its parent's buildscript classpath. Drop the root script and ":app" has
# to resolve spotless into its own "classpath" configuration instead,
# where LockMode.STRICT rejects every artifact as "not part of the
# dependency lock state" -- they are locked in the ROOT lock file, not in
# "app/buildscript-gradle.lockfile". ":app:check" also wires in
# rootProject.tasks.named("spotlessCheck"), which cannot resolve at all
# without the root script.
#
# NOTE: the sonar scanner is resolved here even though this stage never
# runs "sonar" -- applying a plugin resolves its classpath at
# configuration time. It costs a one-off download in a cold-cache image
# build and nothing thereafter.
COPY gradlew                        ./
COPY gradle/                        ./gradle/
COPY settings.gradle.kts            ./
COPY build.gradle.kts               ./
COPY gradle.properties              ./
COPY settings-gradle.lockfile       ./
COPY buildscript-gradle.lockfile    ./
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

# GITHUB_USER/GITHUB_TOKEN are mandatory, not optional: settings.gradle.kts
# resolves the "com.rubensgomes:gradle-catalog" version catalog from GitHub
# Packages while EVALUATING SETTINGS, and every plugin alias comes through
# that catalog, so a cold Docker cache resolves nothing without them.
# Missing credentials produce only a warning, then a confusing HTTP 401 --
# hence the explicit guard below.
#
# WHY BUILD ARGS RATHER THAN "--mount=type=secret"
#   This stage used to take both values as BuildKit secret mounts. It
#   cannot any more: ".github/workflows/build-deploy-image.yml" builds this
#   image with "az acr build", and ACR Tasks runs the CLASSIC Docker
#   builder. "az acr build" has no "--secret" flag and no way to set
#   DOCKER_BUILDKIT=1 (that is only settable via "env:" on a build step
#   inside an "az acr run" task YAML), so every "--mount" here failed with
#   "the --mount option requires BuildKit".
#
#   A build arg is weaker than a secret mount -- the value lands in this
#   stage's layer metadata rather than existing only for the life of one
#   process. It is acceptable here for two reasons:
#
#     1. This is a multi-stage build and "builder" is never tagged or
#        pushed. Only the "runtime" stage below is, and nothing carries
#        these values forward into it. Do NOT promote them to ENV, and do
#        NOT reference them in the extractor or runtime stages.
#     2. "az acr build --secret-build-arg" keeps the values out of the ACR
#        run logs, unlike plain "--build-arg", whose values that command's
#        own help warns are surfaced for debugging.
#
#   Callers pass them without a value ("--build-arg GITHUB_USER"), so
#   Docker reads each one from the ambient environment and the token never
#   appears in the process argv where "ps" could see it. See the
#   "dockerBuild" task in app/build.gradle.kts and the "args:" block in
#   docker-compose.yml.
#
# ACCEPTED COST: the "--mount=type=cache" on /root/.gradle went with them,
# for the same BuildKit reason. ACR agents always start from a cold cache
# so nothing is lost there, but a LOCAL image rebuild now re-resolves
# every dependency. Iterate with "./gradlew :app:bootJar" on the host
# rather than by rebuilding the image.
#
# auto-download=false makes a toolchain mismatch fail in seconds with
# "No matching toolchain" instead of silently pulling a second JDK, should
# anyone later change the base image above.
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
