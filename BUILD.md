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

Always use the wrapper (`./gradlew`), never a locally installed `gradle`. The
wrapper pins **Gradle 9.7.0**.

There is one subproject, `app`, and the root project has no build script, so
`./gradlew build` and `./gradlew :app:build` are equivalent. The examples below
use the short form.

## Prerequisites

| Requirement | Detail |
|---|---|
| JDK to run Gradle | Any recent JDK; it does not have to match the toolchain |
| Build toolchain | **Java 25, Amazon Corretto** — auto-downloaded by Gradle, no manual install |
| `GITHUB_USER` / `GITHUB_TOKEN` | Required to resolve dependencies on a cold cache, and to publish |
| `SONAR_TOKEN` | Required only by the `sonar` task |

The Java 25 Corretto toolchain is declared in `app/build.gradle.kts` and
provisioned automatically by the foojay resolver applied in
`settings.gradle.kts`. Gradle downloads it into `~/.gradle/jdks/` on first use.
Compilation and tests run on that toolchain regardless of which JDK started
Gradle, so builds are reproducible across machines.

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
├── gradle.properties          # developer identity, license, Sonar, Gradle daemon
├── BUILD.md                   # this file
├── .editorconfig              # ktlint rules for *.gradle.kts
└── app/
    ├── build.gradle.kts       # the entire build
    ├── gradle.properties      # coordinates, version, SCM
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
| `scmConnection`, `scmUrl` | POM `<scm>` |

### `gradle.properties` (root) — identity shared across projects

| Property | Drives |
|---|---|
| `developerId`, `developerName`, `developerEmail` | Jar manifest, POM `<developers>` |
| `license`, `licenseUrl` | POM `<licenses>` |
| `mavenRepoPackages` | GitHub Packages URL, for both resolving and publishing |
| `sonar.*` | SonarCloud coordinates |
| `org.gradle.*` | Daemon and logging behaviour |

Read these with the `gradleProperty(name)` helper in `app/build.gradle.kts`,
which fails with an actionable message when a property is missing.

> **Note** — `providers.gradleProperty(...)` does **not** work for values in
> `app/gradle.properties`. It resolves only build-level properties (root
> `gradle.properties`, `GRADLE_USER_HOME`, `-P` flags). The helper uses
> `findProperty` for that reason.

### The `libs` version catalog — dependency versions

`libs` is **not** a local `gradle/libs.versions.toml`. It resolves from the
published catalog `com.rubensgomes:gradle-catalog:0.2.0`, wired up in
`settings.gradle.kts`. It is the single source of truth for every plugin and
library version, including Spring Boot (currently **4.1.0**).

Dependency coordinates in `app/build.gradle.kts` omit versions deliberately —
they come from the Spring Boot BOM, imported via `platform(libs.spring.boot.bom)`.

> **Note** — a `platform()` import applies only to the configuration it is
> declared on and to configurations extending it. `annotationProcessor`,
> `testAnnotationProcessor`, and `developmentOnly` extend nothing, so each
> imports the BOM explicitly. A versionless dependency added to any other
> standalone configuration will need the same, or it fails to resolve with
> `Could not find <group>:<name>:` and no version.

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
`app/src/main/resources/application.yml`.

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

Java and Kotlin sources also get an Apache 2.0 licence header injected.

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

> **Note** — `sonar.projectKey` and `sonar.organization` in the root
> `gradle.properties` are the placeholders `@SONAR_PROJECT_KEY@` and
> `@SONAR_ORGANIZATION@`. Substitute real values before analysis is meaningful.

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
