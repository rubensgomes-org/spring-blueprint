# Toolchain Vendor Switch + SCM Property Move

## Goal

Bring the Dockerfile, build-script comments, and all documentation back in sync
with three uncommitted working-tree changes:

1. `app/build.gradle.kts` — toolchain `vendor` changed `AMAZON` -> `MICROSOFT`
2. `gradle.properties` — `scmConnection` / `scmUrl` moved out of
   `app/gradle.properties` into the root `gradle.properties`
3. `gradle.properties` — `sonar.qualitygate.wait=true` added

## Verified current state

Checked against the working tree on 2026-08-23:

- `./gradlew :app:compileJava` **succeeds**. The foojay resolver provisioned
  `~/.gradle/jdks/microsoft-25-aarch64-os_x.2/jdk-25.0.4.1+1`, and the compile
  task reported `Value of input property 'javaCompiler.metadata.taskInputs.vendor'
  has changed`. So the vendor switch works for a host Gradle build.
- The **Docker build is broken** by the vendor switch. The `builder` stage is
  `FROM amazoncorretto:25`, whose JVM reports vendor `Amazon.com Inc.`, and the
  stage passes `-Porg.gradle.java.installations.auto-download=false`. No
  candidate satisfies `MICROSOFT`, so it fails with "No matching toolchain".
  This is exactly the fast-failure the existing Dockerfile comment predicts.
- `mcr.microsoft.com/openjdk/jdk:25-ubuntu` exists (confirmed against the MCR
  tags endpoint; `25-azurelinux`, `25-mariner`, `25-distroless` also exist).
- Moving the SCM properties does **not** break the build: `gradleProperty()` in
  `app/build.gradle.kts` uses `findProperty`, and root `gradle.properties`
  entries are visible to subprojects.
- `BUILD.md` line ~672 claims `sonar.projectKey` / `sonar.organization` are the
  placeholders `@SONAR_PROJECT_KEY@` / `@SONAR_ORGANIZATION@`. That is already
  stale — both hold real values. `README.md` step 3 repeats the claim.

## Tasks

### Build / container files

- [x] `Dockerfile` — builder base `amazoncorretto:25` ->
      `mcr.microsoft.com/openjdk/jdk:25-ubuntu`
- [x] `Dockerfile` — rewrite the stage-1 NOTE comment to explain the Microsoft
      pin instead of the Corretto one
- [x] `Dockerfile` — drop `RUN dnf install -y findutils` — verified: the image
      ships `/usr/bin/find` and `/usr/bin/xargs`
- [x] `app/build.gradle.kts` — fix the toolchain comment naming Amazon Corretto

### Documentation

- [x] `BUILD.md` — prerequisites table + toolchain paragraph (vendor)
- [x] `BUILD.md` — "Where configuration lives": move the `scmConnection` /
      `scmUrl` row from the `app/gradle.properties` table to the root table
- [x] `BUILD.md` — project-layout tree comments for both properties files
- [x] `BUILD.md` — Docker stages table + "Why Corretto for the builder"
- [x] `BUILD.md` — static-analysis section: drop the stale placeholder note,
      document `sonar.qualitygate.wait=true`
- [x] `README.md` — stack table, `bootRun` toolchain sentence
- [x] `README.md` — template steps 1 and 3
- [x] `llms.txt` — toolchain bullet, both Docker bullets, the
      "project properties live in two files" bullet
- [x] Unrelated staleness found while checking: the wrapper pins Gradle
      **9.7.1**, but `BUILD.md`, `README.md` and `llms.txt` all still said
      9.7.0. Corrected in all three.

### Verification

- [x] `./gradlew build` passes
- [x] `docker compose build` (or `./gradlew dockerBuild`) passes with the new
      builder base

## Review

### What changed

**Build files**

- `Dockerfile` — builder stage `amazoncorretto:25` ->
  `mcr.microsoft.com/openjdk/jdk:25-ubuntu`; the stage-1 NOTE rewritten around
  the Microsoft pin; `RUN dnf install -y findutils` removed.
- `app/build.gradle.kts` — toolchain comment now names the Microsoft Build of
  OpenJDK and states the Dockerfile coupling.

**Documentation**

- `BUILD.md` — prerequisites row, toolchain paragraph, both property tables,
  layout tree, Docker stage table and rationale, Sonar section.
- `README.md` — stack table, `bootRun` paragraph, template steps 1 and 3.
- `llms.txt` — toolchain bullet, both Docker bullets, properties bullet,
  Sonar/credentials bullet.

### Verification performed

- `./gradlew build` — **passed**. Toolchain provisioned to
  `~/.gradle/jdks/microsoft-25-aarch64-os_x.2/jdk-25.0.4.1+1`.
- `docker run mcr.microsoft.com/openjdk/jdk:25-ubuntu` — reports
  `java.vendor = Microsoft`, `java.version = 25.0.4.1`, and ships
  `/usr/bin/find` and `/usr/bin/xargs`. This is what justified deleting the
  `dnf install` line.
- `./gradlew dockerBuild` — **passed**.
- `docker build --no-cache-filter builder` — **passed** in 1m24s, forcing a
  full cold rebuild of the builder stage. The log shows
  `Compiling with toolchain '/usr/lib/jvm/msopenjdk-25-arm64'`, confirming the
  in-image JVM satisfies the spec and no second JDK is downloaded. Tests and
  the 90% JaCoCo gate ran inside the container.

### Notes

- Had the Dockerfile been left alone, `docker build` would have failed with
  "No matching toolchain" while `./gradlew build` on the host kept passing.
  That asymmetry is now called out in `BUILD.md` and `llms.txt`.
- Two pre-existing documentation errors were corrected in passing, both
  unrelated to the working-tree changes: the stale `@SONAR_PROJECT_KEY@` /
  `@SONAR_ORGANIZATION@` placeholder note, and the Gradle version (wrapper is
  on 9.7.1; three docs said 9.7.0).
- The SCM move needed no build-script change: `gradleProperty()` uses
  `findProperty`, and root properties are visible to subprojects.
