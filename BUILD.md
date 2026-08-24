# Build Guide

How the Gradle build for `spring-blueprint` is put together, what each task does,
and when it runs.

- [Quick reference](#quick-reference)
- [Prerequisites](#prerequisites)
- [Project layout](#project-layout)
- [Where configuration lives](#where-configuration-lives)
- [Running the application](#running-the-application)
- [The build lifecycle](#the-build-lifecycle)
- [Verification](#verification)
- [Code formatting](#code-formatting)
- [Artifacts](#artifacts)
- [Docker](#docker)
- [Publishing](#publishing)
- [Releasing](#releasing)
- [Static analysis](#static-analysis)
- [Diagnostics](#diagnostics)
- [Troubleshooting](#troubleshooting)

## Quick reference

| Command | Purpose |
|---|---|
| `./gradlew bootRun` | Run the app locally on port 8080 |
| `./gradlew test` | Run the JUnit 5 suite (coverage report always follows) |
| `./gradlew check` | Format check + tests + 90% coverage gate |
| `./gradlew build` | `check` + assemble all four jars |
| `./gradlew spotlessApply` | Reformat sources in place |
| `./gradlew publishToMavenLocal` | Install into `~/.m2/repository` |
| `./gradlew clean` | Delete `app/build/` |
| `./gradlew :app:dependencies --write-locks` | Regenerate the dependency lock files |
| `./gradlew dockerBuild` | Build the Docker image (requires a running daemon) |
| `docker compose up --build -d` | Build and run the container |
| `docker compose down` | Stop and remove the container |

Always use the wrapper (`./gradlew`), never a locally installed `gradle`. The
wrapper pins **Gradle 9.7.1**.

There is one subproject, `app`, and the root project has no build script, so
`./gradlew build` and `./gradlew :app:build` are equivalent. The examples below
use the short form.

## Prerequisites

| Requirement | Detail |
|---|---|
| JDK to run Gradle | Any recent JDK; it does not have to match the toolchain |
| Build toolchain | **Java 25, Microsoft Build of OpenJDK** — auto-downloaded by Gradle, no manual install |
| `GITHUB_USER` / `GITHUB_TOKEN` | Required to resolve dependencies on a cold cache, and to publish |
| `SONAR_TOKEN` | Required only by the `sonar` task |

The Java 25 Microsoft toolchain is declared in `app/build.gradle.kts` and
provisioned automatically by the foojay resolver applied in
`settings.gradle.kts`. Gradle downloads it into `~/.gradle/jdks/` on first use.
Compilation and tests run on that toolchain regardless of which JDK started
Gradle, so builds are reproducible across machines.

The vendor is pinned to `JvmVendorSpec.MICROSOFT`, not left open. Changing it
also means changing the Docker builder base image, which is deliberately chosen
to satisfy this same spec — see [Docker](#docker).

### GitHub Packages credentials

Dependencies — including the shared `com.rubensgomes:gradle-catalog` version
catalog — resolve from a private GitHub Packages repository:

```bash
export GITHUB_USER=<your-github-username>
export GITHUB_TOKEN=<a-PAT-with-read:packages>
```

Once artifacts are in the Gradle module cache the build resolves them offline
and these variables are not needed. They matter on a **fresh clone**. If they
are unset, the build prints a warning naming both variables rather than failing
with an unexplained HTTP 401.

## Project layout

```
spring-blueprint/
├── settings.gradle.kts        # project inclusion, repositories, version catalog
├── settings-gradle.lockfile   # lock state: version catalog resolution
├── gradle.properties          # developer identity, license, SCM, Sonar, Gradle daemon
├── BUILD.md                   # this file
├── .editorconfig              # ktlint rules for *.gradle.kts
├── .github/workflows/
│   └── build-verify.yml       # CI: compile, test, check, sonar on push to main
└── app/
    ├── build.gradle.kts       # the entire build
    ├── gradle.lockfile        # lock state: application dependencies
    ├── buildscript-gradle.lockfile  # lock state: plugin classpath
    ├── gradle.properties      # coordinates, version
    └── src/{main,test}/...
```

## Where configuration lives

Nothing about the build is hard-coded in more than one place. Values come from
three sources.

### `app/gradle.properties` — this module's identity

| Property | Drives |
|---|---|
| `group` | Maven groupId |
| `version` | Project version; **must end in `-SNAPSHOT`** for the release plugin |
| `artifactId` | Archive base name, jar manifest, published artifactId |
| `title` | `Specification-Title` manifest attribute, POM `<name>` |
| `description` | POM `<description>` |
| `mainClass` | Spring Boot entry point (`Start-Class`) |

### `gradle.properties` (root) — identity shared across projects

| Property | Drives |
|---|---|
| `developerId`, `developerName`, `developerEmail` | Jar manifest, POM `<developers>` |
| `license`, `licenseUrl` | POM `<licenses>` — currently `MIT License`; must match the root `LICENSE` file |
| `mavenRepoPackages` | GitHub Packages URL, for both resolving and publishing |
| `scmConnection`, `scmUrl` | POM `<scm>`, and the published POM `<url>` |
| `sonar.*` | SonarCloud coordinates and quality-gate behaviour |
| `org.gradle.*` | Daemon and logging behaviour |

Read these with the `gradleProperty(name)` helper in `app/build.gradle.kts`,
which fails with an actionable message when a property is missing.

> **Note** — `providers.gradleProperty(...)` does **not** work for values in
> `app/gradle.properties`. It resolves only build-level properties (root
> `gradle.properties`, `GRADLE_USER_HOME`, `-P` flags). The helper uses
> `findProperty` for that reason.

### The `libs` version catalog — dependency versions

`libs` is **not** a local `gradle/libs.versions.toml`. It resolves from the
published catalog `com.rubensgomes:gradle-catalog:0.2.7`, wired up in
`settings.gradle.kts`. It is the single source of truth for every plugin and
library version, including Spring Boot (currently **4.1.1**).

Dependency coordinates in `app/build.gradle.kts` omit versions deliberately —
they come from the Spring Boot BOM, imported via `platform(libs.spring.boot.bom)`.

> **Note** — a `platform()` import applies only to the configuration it is
> declared on and to configurations extending it. `annotationProcessor`,
> `testAnnotationProcessor`, and `developmentOnly` extend nothing, so each
> imports the BOM explicitly. A versionless dependency added to any other
> standalone configuration will need the same, or it fails to resolve with
> `Could not find <group>:<name>:` and no version.

### Dependency locking — reproducible resolution

Versionless coordinates keep the build script readable, but on their own they
make the *transitive* graph a moving target: the same source tree can resolve
different transitive versions on different days. Dependency locking pins the
fully resolved graph.

Three lock files, with different scopes:

| File | Locks | Configured in |
|---|---|---|
| `settings-gradle.lockfile` | the `libs` catalog resolution (`incomingCatalogForLibs0`) | nothing — Gradle locks version-catalog configurations automatically |
| `app/gradle.lockfile` | `annotationProcessor`, `compileClasspath`, `developmentOnly`, `runtimeClasspath`, `testAnnotationProcessor`, `testCompileClasspath`, `testRuntimeClasspath` | the "Dependency Locking" section of `app/build.gradle.kts` |
| `app/buildscript-gradle.lockfile` | the plugin `classpath` — the libraries the Gradle plugins themselves pull in and run inside the build | the "Buildscript Classpath Locking" section of `app/build.gradle.kts` |

The tooling's own *resolvable configurations* — `jacocoAgent`, `jacocoAnt` and
friends — are deliberately left out of `app/gradle.lockfile`. None reaches the
compiled output, and each would rewrite that file on every routine tooling bump.
This is separate from the plugin **classpath**, which is locked in
`app/buildscript-gradle.lockfile`: the JARs implementing Spotless, SonarQube and
the release plugin execute inside the build, so their transitive closure is worth
pinning even though the coverage tooling's runtime graph is not.

> **Note** — a lock file is a *forcing constraint*, not a checksum that
> resolution is merely compared against. Where the lock and the Spring Boot BOM
> disagree, the lock wins and the BOM's version is downgraded or upgraded to
> match. Hand-editing a version in `app/gradle.lockfile` to another version
> that exists will therefore silently change the build rather than fail it.
> Never edit these files by hand; regenerate them.

The plugin *versions* are already pinned by the catalog, so
`app/buildscript-gradle.lockfile` adds the plugins' own transitive closure —
code that runs inside the build but was previously unpinned.

> **Note** — `LockMode.STRICT` is set **twice** in `app/build.gradle.kts`, once
> inside `buildscript { }` and once on the project. The two are independent: the
> project-level setting does not reach the plugin classpath. With only the
> project one set, deleting `app/buildscript-gradle.lockfile` leaves the build
> passing silently. Removing either `lockMode` line reintroduces that blind spot
> for its own graph.

Locking runs in `LockMode.STRICT`, so a missing or half-merged lock file is a
build failure rather than a silent fall back to "whatever is newest":

```
> Locking strict mode: Configuration ':app:compileClasspath' is locked but does not have lock state.
```

A dependency that resolves but is absent from the lock state fails the same way:

```
> Resolved 'org.springframework:spring-core:7.0.9' which is not part of the dependency lock state
```

Both messages mean the same thing in practice — **regenerate the lock file**:

```bash
./gradlew :app:dependencies --write-locks
```

`GITHUB_USER` and `GITHUB_TOKEN` must be exported for that command. Lock state
is written from a real resolution against the remote repositories, so it cannot
be produced with `--offline` from a warm Gradle cache.

Regenerate whenever any of these change:

- a dependency is added to or removed from `app/build.gradle.kts`
- the `com.rubensgomes:gradle-catalog` version in `settings.gradle.kts` changes
  — the Spring Boot BOM version flows from the catalog, so the entire
  transitive closure shifts even though nothing in `app/build.gradle.kts` was
  touched
- a locked configuration is added to or removed from `lockedConfigurations`

All three lock files are committed to source control. Never pass `--write-locks` in
an automated build: it would rewrite the lock state to match whatever resolved
at that moment, which is precisely the unpredictability locking exists to
prevent.

### Spring profiles — three YAML files

| File | Profile | Activated by |
|---|---|---|
| `application.yml` | default | always |
| `application-local.yml` | `local` | `tasks.bootRun` sets `spring.profiles.active` |
| `application-docker.yml` | `docker` | `SPRING_PROFILES_ACTIVE`, baked into the image |

The default profile pins `logging.level.root` to `error` so a deployed service
stays quiet. That also discards Spring Boot's own startup messages — `Starting
App`, `Tomcat started on port 8080`, `Started App in Xs` — leaving only the ASCII
banner, which is written straight to `System.out` rather than through SLF4J. The
result looks exactly like a hang.

Both `local` and `docker` exist primarily to raise `root` back to `info`. If you
add a profile of your own and it appears to start silently, this is why.

> **Note** — `logging.level.com.rubensgomes.blueprint` is set to `trace` in the
> default profile. It must match the real package; an out-of-date value here
> silently falls back to `root`.

### Resource filtering — `@artifactId@`

`spring.application.name` in `application.yml` is the literal token
`@artifactId@`, replaced at `processResources` time with the `artifactId`
property from `app/gradle.properties`. Editing the literal has no effect —
change `artifactId` instead.

`ReplaceTokens` with `@...@` delimiters is used rather than Gradle's `expand()`,
because `expand()` evaluates `${...}` through the Groovy template engine — the
same syntax Spring uses for its own placeholders. A future
`${DB_HOST:localhost}` would break the build or be silently substituted away.
The filter applies to `application*.yml`, so every profile file passes through
it.

## Running the application

```bash
./gradlew bootRun
```

Serves on **port 8080** with a graceful 5s shutdown. The sample endpoint:

```bash
curl http://localhost:8080/api/v1/helloworld
```

Actuator is on the classpath for health and metrics endpoints. Spring Boot
DevTools is active under `bootRun` for automatic restart, and is excluded from
the packaged jar. Runtime configuration lives in
`app/src/main/resources/application.yml`, with `application-local.yml` layered
on top — `bootRun` activates the `local` profile automatically.

### Error responses

Every failed request returns JSON, not Spring Boot's Whitelabel HTML page.
`GlobalErrorController` implements `ErrorController` and maps `/error`, which the
servlet container forwards to after any `sendError`, so one handler covers 404s,
validation 400s and unhandled 500s alike:

```bash
curl http://localhost:8080/nope
# {"timestamp":"...","status":404,"error":"Not Found","message":"...","path":"/nope"}
```

This changes the error *representation* only. An unmapped path still returns
404 — it just returns it in a form a REST client can parse.

The `/error` mapping enumerates its HTTP methods explicitly — `GET`, `HEAD`,
`POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` — rather than relying on the
`@RequestMapping` default, which silently accepts everything including `TRACE`.

> **Do not narrow that list to `GET`.** It looks like an obvious tightening and
> it breaks error handling. The container forwards the *original* request method
> when it dispatches to `/error`, so a request that failed as a POST arrives as a
> POST. With a GET-only mapping, every non-GET failure returns
> `405 Method Not Allowed` instead of the status it actually caused — a client
> POSTing to a mistyped URL would be told its method was wrong rather than that
> the path does not exist.
>
> `TRACE` is the one deliberate omission: the application never serves it, and
> echoing a request back is a cross-site tracing liability.

Note that the unit tests call `handleError` directly and never exercise the
mapping, so they would **not** catch a regression in that method list. Verify it
against a running server:

```bash
for m in GET POST PUT PATCH DELETE; do curl -s -o /dev/null -w "$m %{http_code}\n" -X $m localhost:8080/nope; done
# every line should read 404, not 405
```

To run the packaged executable jar instead:

```bash
./gradlew bootJar
java -jar app/build/libs/spring-blueprint-<version>.jar
```

## The build lifecycle

```
build
├── assemble
│   ├── bootJar ──────────► check          ← packaging depends on verification
│   │                        ├── jacocoTestCoverageVerification
│   │                        │    └── jacocoTestReport
│   │                        │         └── test
│   │                        ├── spotlessCheck
│   │                        └── test
│   ├── jar
│   ├── javadocJar ───────► javadoc
│   └── sourcesJar
└── check
```

Five pieces of wiring are worth knowing, because they are not Gradle defaults:

1. **`bootJar` depends on `check`.** Running `./gradlew bootJar` executes the
   full test suite, coverage gate, and format check first. An unverified
   executable jar cannot be produced.
2. **`test` is *finalized by* `jacocoTestReport`.** The coverage report is
   written even when tests fail. Finalizers do not appear in dependency trees.
3. **`jacocoTestCoverageVerification` is wired into `check`.** This is what
   makes the coverage threshold binding rather than advisory.
4. **`sonar` depends on `check`**, so analysis never runs against unverified
   code and the XML coverage report is guaranteed to exist by then.
5. **`spotlessCheck` verifies but never rewrites.** Formatting is not applied
   automatically during compilation; see [Code formatting](#code-formatting).

Inspect any of this yourself with the `task-tree` plugin:

```bash
./gradlew build taskTree --no-repeat
```

## Verification

### Tests

```bash
./gradlew test                          # whole suite
./gradlew test --tests '*HelloWorld*'   # filtered
```

JUnit 5 on the JUnit Platform. The `test` task passes
`-XX:+EnableDynamicAgentLoading` so the JVM does not warn about JaCoCo's agent
being loaded dynamically.

### Coverage

Enforced at **90% line and 90% branch coverage**, evaluated across the whole
`app` bundle, and wired into `check`:

```bash
./gradlew jacocoTestCoverageVerification
```

A violation fails the build with the measured ratio:

```
Rule violated for bundle app: lines covered ratio is 0.84, but expected minimum is 0.90
```

Reports land in:

| Format | Location | Consumer |
|---|---|---|
| HTML | `app/build/jacocoHtml/index.html` | humans |
| XML | `app/build/reports/jacoco/test/jacocoTestReport.xml` | SonarQube |

CSV output is disabled.

Two properties of this rule matter as the codebase grows. It is a **bundle
average**, not a per-class floor — a class with poor coverage can hide behind
well-covered ones. And **Lombok-generated members count toward the total**, so
generated builders and accessors need tests like any other code. To exempt
generated or configuration classes, add an `excludes` list inside the rule:

```kotlin
excludes = listOf("com.rubensgomes.blueprint.config.*")
```

## Code formatting

Spotless enforces formatting; it does **not** run automatically during
compilation. Deliberately so: rewriting sources before every compile would mean
`spotlessCheck` only ever inspects files that were just reformatted, making the
gate decorative.

```bash
./gradlew spotlessApply      # fix formatting
./gradlew spotlessCheck      # verify only (runs as part of check)
```

| Files | Formatter | Notes |
|---|---|---|
| `src/**/*.java` | Google Java Format | 2-space indent, 100 columns; also removes unused imports and sorts them |
| `src/**/*.kt` | ktfmt | |
| `src/**/*.json` | Jackson | |
| `src/**/*.yaml`, `*.yml` | whitespace only | Jackson would delete every comment |
| `*.gradle.kts` | ktlint | driven by the root `.editorconfig` |

Java and Kotlin sources also get a licence header injected — an
`SPDX-License-Identifier: MIT` tag, the copyright line, and a pointer to
`LICENSE` for the project's AI-content disclosures. The text is the
`licenseHeaderText` constant in `app/build.gradle.kts`; edit it there, then run
`./gradlew spotlessApply` to restamp every file.

> **Note** — `licenseHeader` is configured on the `java` and `kotlin` formats
> only, which target `src/**`. The headers on `settings.gradle.kts` and
> `app/build.gradle.kts` carry the same licence text but are **not** managed by
> Spotless and will not be restamped; they have to be edited by hand.

Two ktlint constraints apply to the header on `app/build.gradle.kts`, and both
fail `spotlessKotlinGradleCheck` rather than being auto-fixed:

- It must be **one** block comment. The licence text is merged into the same
  comment as the script documentation, separated by a dashed rule, because
  `standard:no-consecutive-comments` rejects "a block comment ... preceded by a
  block comment".
- It must be a plain block comment, never KDoc — see the note below.

`settings.gradle.kts` escapes both, but only by accident: the `kotlinGradle`
target is `target("*.gradle.kts")`, which resolves **relative to the `app`
project**, so the root settings script is not linted by any Spotless step.

To have formatting verified before every push:

```bash
./gradlew spotlessInstallGitPrePushHook
```

> **Note** — the header comment at the top of `app/build.gradle.kts` is a plain
> block comment, not KDoc. ktlint's `standard:kdoc` rule rejects KDoc at the top
> level of a Gradle script, so converting it will fail
> `spotlessKotlinGradleApply`.

## Artifacts

`./gradlew assemble` produces four archives in `app/build/libs/`:

| Archive | Contents |
|---|---|
| `spring-blueprint-<version>.jar` | executable Spring Boot jar (layered, for Docker caching) |
| `spring-blueprint-<version>-plain.jar` | library jar, classes only |
| `spring-blueprint-<version>-sources.jar` | sources |
| `spring-blueprint-<version>-javadoc.jar` | Javadoc |

The base name comes from the `artifactId` property, not the `app` directory
name. Every jar carries full manifest metadata — `Specification-Title`,
`Implementation-Title`, `Implementation-Version`, `Implementation-Vendor`,
`Built-By`, `Build-Jdk`, `Created-By` — and the executable jar additionally gets
`Start-Class` and the `Spring-Boot-*` entries from the Boot plugin.

Layered jars are enabled by default; no configuration is needed. To build an OCI
image with Cloud Native Buildpacks:

```bash
./gradlew bootBuildImage
```

For the hand-written multi-stage image, see the next section — it consumes those
layers directly.

## Docker

```bash
docker compose up --build -d      # build and start
docker compose logs -f app        # follow logs
docker compose ps                 # check health
docker compose down               # stop and remove
```

There is also a Gradle entry point, useful when CI wants a single `./gradlew`
invocation to be the whole pipeline:

```bash
./gradlew dockerBuild      # tags <artifactId>:<version> and <artifactId>:local
```

It is a plain `Exec` task shelling out to the `docker` CLI. It deliberately does
**not** depend on `bootJar`, because the Dockerfile compiles the application in
its own builder stage — depending on the host jar would run the whole suite
twice to produce an artifact the image never uses. It is deliberately not a
dependency of `build` either, so an ordinary `./gradlew build` never requires a
Docker daemon.

### Image version labels

The two build paths stamp `org.opencontainers.image.version` differently:

| Built with | Tags | `image.version` label |
|---|---|---|
| `./gradlew dockerBuild` | `<artifactId>:<version>` and `<artifactId>:local` | the real project version |
| `docker compose build` | `<artifactId>:local` | `unknown` |

That asymmetry is intentional. `APP_VERSION` used to be hardcoded in
`docker-compose.yml`, which made it a second source of truth for the project
version — and it went stale the first time the release plugin bumped the
version. Compose now passes nothing and inherits the Dockerfile's `unknown`
default, which is honest; `dockerBuild` reads the version from
`app/gradle.properties`, so it cannot drift.

Use `./gradlew dockerBuild` for anything you intend to publish or keep.

The shared catalog does expose `com.bmuschko.docker-remote-api`, but that plugin
drives the Docker Engine REST API, which uses the **legacy builder**. This
Dockerfile requires BuildKit for its `--mount=type=secret` credentials, and on
the legacy builder those mounts do not exist — Gradle inside the container would
fail on the version catalog with an HTTP 401. Hence the CLI shell-out.

`GITHUB_USER` and `GITHUB_TOKEN` must be exported first. They are the same
credentials the Gradle build needs, passed through as **BuildKit secrets** —
build-time only, never written into an image layer or `docker history`.

### The three stages

| Stage | Base | Does |
|---|---|---|
| `builder` | `mcr.microsoft.com/openjdk/jdk:25-ubuntu` | Runs `./gradlew :app:bootJar` |
| `extractor` | `eclipse-temurin:25-jre-alpine` | Explodes the layered jar |
| `runtime` | `eclipse-temurin:25-jre-alpine` | Non-root JRE image |

**Why a Microsoft base for the builder.** The build pins
`vendor = JvmVendorSpec.MICROSOFT` alongside `languageVersion = 25`. Gradle
treats the JVM running Gradle as a toolchain candidate, and this base reports
`java.vendor` `Microsoft`, so the spec is satisfied by the JVM already in the
image and the foojay resolver never fires. A Temurin, Corretto or `gradle:*`
builder would download a second ~200 MB JDK on every cold build. The Dockerfile
also passes `-Porg.gradle.java.installations.auto-download=false`, so if the
base image is ever changed to a non-Microsoft one the build fails in seconds
with "No matching toolchain" rather than silently paying that download on every
run.

**The toolchain vendor and the builder base are one decision.** Changing
`vendor` in `app/build.gradle.kts` without changing `FROM` in the Dockerfile
breaks `docker build` while leaving host builds green, because the host has the
foojay resolver available and the builder stage deliberately does not.

**The builder needs no `findutils` install.** The Gradle wrapper hard-requires
`xargs` and aborts with `xargs is not available` before doing anything else, and
the jar-selection step uses `find`. The Ubuntu-based Microsoft image ships both
already — unlike the Amazon Linux *minimal* images, which shipped neither and
needed an explicit `dnf install`. Do not move to a slimmer base such as
`25-distroless` without re-checking both tools.

**Why alpine for the runtime.** busybox supplies `wget` for the `HEALTHCHECK` at
no extra size; the Ubuntu-based Temurin JRE images ship neither `wget` nor
`curl` and would need an `apt-get` layer. A JRE cannot run a single-file
source-launch probe instead, because it has no compiler.

**Layer extraction.** Spring Boot 4 **removed** the `layertools` jarmode. The
jar bundles `spring-boot-jarmode-tools`, so extraction is:

```bash
java -Djarmode=tools -jar application.jar extract --layers --launcher --destination ...
```

The runtime stage copies the four layers least-churn-first —
`dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`.
Boot writes constant 1980 timestamps, so the ~23 MB `dependencies` layer is
byte-identical between builds and registries deduplicate it. This buys push and
pull efficiency, not local build time: any source change still recompiles.

### Verification runs inside the image build

`tasks.bootJar` depends on `check`, so `docker build` runs spotless, the full
JUnit suite, and the 90% line/branch coverage gate. That is deliberate — the
image cannot be built from code that has not passed verification. It is also why
`app/src/test` and `.editorconfig` are in the build context.

For iterating on the Dockerfile itself:

```bash
docker build --build-arg GRADLE_BUILD_ARGS="-x check" ...
```

`-x check` prunes the entire verification subgraph. The default is empty, and CI
must never set it.

### The `docker` profile

`app/src/main/resources/application-docker.yml` is activated by
`SPRING_PROFILES_ACTIVE=docker`, which the image bakes in as a default. It
exists mainly to raise `logging.level.root` from the default `error` to `info` —
without it a container prints the Spring banner and then nothing at all, which
is indistinguishable from a hang. It also pins ANSI output off and enables the
`/actuator/health/liveness` and `/actuator/health/readiness` probes.

Anything that varies per deployment — published port, memory limits,
credentials — belongs in `docker-compose.yml`, not in that file.

### Runtime notes

- The `ENTRYPOINT` is exec form, so the JVM is PID 1 and `docker stop` delivers
  SIGTERM straight to it, triggering `server.shutdown: graceful`. Wrapping it in
  `sh -c` to expand a `$JAVA_OPTS` would make `sh` PID 1, and `sh` does not
  forward SIGTERM — graceful shutdown would silently become a 10s SIGKILL.
  `JDK_JAVA_OPTIONS` provides that configurability without a shell.
- `-XX:MaxRAMPercentage=75.0` is meaningless without a container memory limit;
  without one it computes 75% of host RAM. That flag and the compose memory
  limit are a package deal.
- `-XX:+UseG1GC` is explicit because the JVM only auto-selects G1 at 2+ CPUs
  **and** 1792 MB+; at the 1g compose limit it would otherwise pick SerialGC.
- Setting `JDK_JAVA_OPTIONS` in compose **replaces** the image's value rather
  than appending to it.

## Publishing

| Command | Target | Credentials |
|---|---|---|
| `./gradlew publishToMavenLocal` | `~/.m2/repository` | none |
| `./gradlew publish` | GitHub Packages | `GITHUB_USER` + `GITHUB_TOKEN` |
| `./gradlew generatePomFileForMavenPublication` | `app/build/publications/maven/` | none |

The publication carries a complete POM — name, description, URL, licence,
developer, and SCM — assembled from the two `gradle.properties` files, plus the
sources and Javadoc jars.

`publish` does **not** depend on `check`. Run `./gradlew build publish` to verify
before uploading.

> **Note** — the Spring Boot plugin gives the `jar` task the `plain` classifier
> so it does not collide with the executable jar. Every artifact in the `java`
> component is therefore classified, which leaves the generated POM with
> `<packaging>pom</packaging>` and no main artifact. Gradle consumers resolve
> correctly through the published Gradle module metadata; a plain Maven consumer
> would not. If this module ever needs to be resolvable from Maven, swap the
> classifiers:
>
> ```kotlin
> tasks.jar { archiveClassifier.set("") }
> tasks.bootJar { archiveClassifier.set("boot") }
> ```

## Releasing

```bash
./gradlew release
```

Strips `-SNAPSHOT`, tags, merges to the `release` branch, bumps to the next
snapshot, and pushes.

**Preconditions:**

- current branch is `main` (`requireBranch`)
- working tree is clean, with no unpushed or unpulled changes
- `version` in `app/gradle.properties` ends in `-SNAPSHOT`
- no SNAPSHOT dependencies

`release.useAutomaticVersion=true` in the root `gradle.properties` suppresses
the interactive version prompts. The plugin orchestrates its chain at execution
time, so it does not show up in `taskTree`:

```
createScmAdapter → initScmAdapter → checkCommitNeeded → checkUpdateNeeded
→ prepareVersions → checkoutMergeToReleaseBranch → unSnapshotVersion
→ confirmReleaseVersion → checkSnapshotDependencies → runBuildTasks
→ preTagCommit → createReleaseTag → checkoutMergeFromReleaseBranch
→ updateVersion → commitNewVersion
```

`runBuildTasks` runs the full `build`, so a release executes tests and the
coverage gate.

> **Note** — this plugin is incompatible with the Gradle configuration cache,
> which is why `org.gradle.configuration-cache=false` is set in the root
> `gradle.properties`.

## Static analysis

```bash
export SONAR_TOKEN=<token>
./gradlew sonar
```

Depends on `check`, so tests and the coverage report always precede analysis.
The `sonarqube` task is a deprecated alias for `sonar`.

All coordinates live in the root `gradle.properties`:

| Property | Value |
|---|---|
| `sonar.host.url` | `https://sonarcloud.io` |
| `sonar.organization` | `rubensgomes-org` |
| `sonar.projectKey` | `rubensgomes-org_spring-blueprint` |
| `sonar.projectName` | `spring-blueprint` |
| `sonar.qualitygate.wait` | `true` |

`sonar.qualitygate.wait=true` makes `sonar` **block** after uploading, poll
until SonarCloud finishes processing, and then fail the build when the quality
gate fails. The default is `false`, where the task succeeds the moment the
upload is accepted and a failing gate is something you only find out about in
the SonarCloud UI. The cost of the stricter setting: the task now takes as long
as server-side analysis, and `SONAR_TOKEN` must be able to read gate status, not
just submit.

Cloning this project as a template means replacing `sonar.organization`,
`sonar.projectKey`, and `sonar.projectName` with your own.

## Continuous integration

`.github/workflows/build-verify.yml` runs the same gate on every push to `main`,
through the committed wrapper, so CI and a workstation execute identical Gradle.

One job, `build-verify`, on `ubuntu-latest`. Three setup steps, then the four
verification phases:

| Step | Command | What it adds |
|---|---|---|
| `compile` | `:app:classes :app:testClasses` | `processResources`, `compileJava`, `compileTestJava` |
| `test` | `:app:test` | `test`, `jacocoTestReport` |
| `check` | `:app:check` | `spotless*Check`, `jacocoTestCoverageVerification` |
| `sonar` | `:app:sonar` | `sonarResolver`, `sonar` |

### Why four invocations instead of one

`sonar` already depends on `check`, which depends on `test`, so `./gradlew
:app:sonar` alone would run everything. Splitting it gives four independently
red/green steps, so a failure names a phase instead of burying it in one 23-task
log.

It is not wasteful. All four steps share a workspace and a daemon, and
up-to-date state persists in `app/.gradle`, not in daemon memory — each step
finds the previous step's work `UP-TO-DATE` and adds only its own. In
particular, `test` does **not** re-run during `check`, and Spotless runs once.
The real cost is configuration time ×4, because the release plugin forces
`org.gradle.configuration-cache=false`.

### Required secrets

| Secret | Used as | Notes |
|---|---|---|
| `RUBENS_PAT_TOKEN` | `GITHUB_TOKEN` | Classic PAT with `read:packages` |
| `SONAR_TOKEN` | `SONAR_TOKEN` | Must be able to **read quality gate status**, not just submit |

Both are organization-level secrets shared with this repository.

`GITHUB_USER` and `GITHUB_TOKEN` cannot be declared in a workflow `env:` block —
GitHub reserves the `GITHUB_` prefix. The values therefore ride in
`PACKAGES_USER` / `PACKAGES_TOKEN` and each step exports the real names into its
own shell. They are needed by **every** invocation, not just the first, because
`settings.gradle.kts` reads them while evaluating settings.

### Toolchain and the vendor pin

The workflow installs the JDK with `actions/setup-java`, `distribution:
microsoft`, `java-version: 25` — matching `JvmVendorSpec.MICROSOFT` so Gradle
reuses the JVM it is already running on. It then passes
`-Porg.gradle.java.installations.auto-download=false`, exactly as the Dockerfile
does, so a drift between the vendor pin and the runner distribution fails in
seconds with "No matching toolchains" instead of silently downloading a second
JDK on every run. Expect the runner's preinstalled Temurin JDKs to appear in
that error as detected-but-rejected — that is the pin working.

Change the vendor in `app/build.gradle.kts` and you must change **three** places
in step: the toolchain block, the Dockerfile `FROM`, and `distribution:` here.

### Other details worth knowing

- **`fetch-depth: 0`.** SonarCloud derives New Code detection, blame, and issue
  backdating from git history; a shallow clone degrades analysis silently.
- **`shell: bash` is pinned** on all four steps. They expand `$GRADLE_ARGS`
  unquoted and so depend on word splitting — bash splits, zsh does not.
- **Never add `--write-locks`.** Locking runs in `LockMode.STRICT`; CI's job is
  to fail on lock drift, not to paper over it.
- **`cancel-in-progress: false`.** `main` is the verification gate, so every
  commit gets a verdict rather than only the newest.
- **The release plugin re-triggers CI.** It pushes two commits per release to
  `main`, each costing a run and a SonarCloud analysis. The workflow's closing
  comment carries the `if:` guard to suppress them if that ever matters.

## Diagnostics

```bash
./gradlew :app:tasks                         # everything runnable
./gradlew <task> taskTree --no-repeat        # why does X run Y?
./gradlew :app:dependencies --configuration runtimeClasspath
./gradlew :app:dependencyInsight --dependency lombok --configuration compileClasspath
./gradlew :app:javaToolchains                # JDKs Gradle found
./gradlew :app:buildEnvironment              # resolved plugin versions
./gradlew :app:properties                    # all project properties
./gradlew <task> --dry-run                   # execution plan, nothing run
```

`org.gradle.logging.level=info` in the root `gradle.properties` makes every
command verbose. Override per invocation:

```bash
./gradlew build -Dorg.gradle.logging.level=lifecycle --console=plain
```

## Troubleshooting

**`Could not find <group>:<name>:` with no version**

A versionless dependency was added to a configuration the Spring Boot BOM does
not reach. Add `<configuration>(platform(libs.spring.boot.bom))` alongside it.
See [Where configuration lives](#where-configuration-lives).

**HTTP 401 resolving dependencies**

`GITHUB_USER` / `GITHUB_TOKEN` are unset or the token lacks `read:packages`. The
build warns about this at startup.

**`The following files had format violations`**

Run `./gradlew spotlessApply`.

**`Rule violated for bundle app: lines covered ratio is ...`**

Coverage fell below 90%. Open `app/build/jacocoHtml/index.html` to find the
uncovered code.

**`Required property '<name>' not found in gradle.properties`**

A property the build needs was removed from `app/gradle.properties` or the root
`gradle.properties`. See [Where configuration lives](#where-configuration-lives).

**Release fails on a dirty working tree**

`checkCommitNeeded` refuses to release with uncommitted or untracked files.
Commit or stash first.
