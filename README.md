# spring-blueprint

A basic Spring Boot RESTful application to serve as:

1. **A blueprint to copy.** A complete, opinionated Gradle build — toolchains,
   formatting, coverage gates, publishing, releasing, static analysis — already
   wired together and working, ready to be cloned as the starting point for a
   new project.
2. **A demonstration.** Spring Boot layering and design patterns shown in code
   small enough to read in one sitting.
3. **A CI/CD reference.** GitHub Actions workflows that build, verify, and cut
   a release.

## Quick start

```bash
git clone https://github.com/rubensgomes-org/spring-blueprint.git
cd spring-blueprint
```

Export your GitHub Packages credentials first — dependencies, including the
shared version catalog, resolve from a private repository:

```bash
export GITHUB_USER=<your-github-username>
export GITHUB_TOKEN=<a-PAT-with-read:packages>
```

Then run it either way. Both serve on **port 8080**, so run one at a time.

### Option 1 — Gradle

```bash
./gradlew bootRun
```

No JDK setup required: Gradle downloads the Java 25 Microsoft Build of OpenJDK
toolchain on first build. DevTools is active, so edits to `src/main` restart the
app automatically. Activates the `local` profile.

### Option 2 — Docker

```bash
docker compose up --build -d      # build and start
docker compose logs -f app        # follow the logs
docker compose down               # stop and remove
```

Needs nothing installed but Docker — the image compiles the application in its
own builder stage. Activates the `docker` profile.

### Verify either one

```bash
curl http://localhost:8080/api/v1/helloworld
# {"message":"Hello World!"}

curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Stopping it

**Gradle** — <kbd>Ctrl</kbd>+<kbd>C</kbd> in the `bootRun` terminal. Shutdown is
graceful, but its logs are discarded and Gradle then prints `BUILD FAILED`. Both
are expected; [BUILD.md](BUILD.md#stopping-the-application) explains why, and
how to watch the shutdown instead.

**Docker** — `docker compose down`. SIGTERM reaches the JVM as PID 1, so the
same graceful shutdown applies.

### Which to use

|                           | `./gradlew bootRun`    | `docker compose up`              |
|---------------------------|------------------------|----------------------------------|
| Best for                  | Writing code           | Checking it runs as it will ship |
| Hot restart               | Yes, via DevTools      | No — rebuild the image           |
| Profile                   | `local`                | `docker`                         |
| Runs the test suite first | No                     | Yes, inside the build            |
| Startup                   | ~5s                    | ~1s once built (no DevTools)     |
| Needs credentials         | On a cold Gradle cache | On every image build             |

Credentials are only ever needed at **build** time; see
[BUILD.md](BUILD.md#github-packages-credentials).

## The stack

| Layer      | Choice                                            |
|------------|---------------------------------------------------|
| Language   | Java 25 (Microsoft Build of OpenJDK toolchain)    |
| Framework  | Spring Boot 4.1.1 — Web MVC, Actuator, Validation |
| Build      | Gradle 9.7.1, Kotlin DSL                          |
| Versions   | Shared catalog `com.rubensgomes:gradle-catalog`   |
| Testing    | JUnit 5, Mockito, AssertJ, Spring Test            |
| Coverage   | JaCoCo, enforced at 90% line and branch           |
| Formatting | Spotless — Google Java Format, ktfmt, ktlint      |
| Analysis   | SonarCloud                                        |
| Release    | `net.researchgate.release`                        |
| Publishing | GitHub Packages                                   |
| Container  | Multi-stage Docker build on eclipse-temurin JRE   |
| CI         | GitHub Actions — verify on push, manual release   |

## Versioning

The build uses locked dependency versions. Any catalog change that moves a
library or framework version requires regenerating the lock files:

```bash
./gradlew :app:dependencies --write-locks
```

Locking runs in strict mode — a missing lock file fails the build rather than
quietly resolving whatever is newest. Details in
[BUILD.md](BUILD.md#dependency-locking--reproducible-resolution).

## What it demonstrates

### Layered architecture

```
HelloWorldRestController  →  HelloWorldService  →  MessageResponse
(web/controller)             (service)             (model/response)
```

The controller owns HTTP concerns only — routing, status codes, content
negotiation — and delegates every business decision to the service layer. The
service returns a model type rather than a raw string, so the response contract
survives the business logic changing underneath it. Swapping in a real domain
layer means editing one class.

### Patterns in the code

| Pattern                | Where                                              | Why it's there                                        |
|------------------------|----------------------------------------------------|-------------------------------------------------------|
| Constructor injection  | `HelloWorldRestController`                         | Explicit, final dependencies; testable without Spring |
| Immutable value object | `MessageResponse`                                  | A `record` — safe to share across threads             |
| Observer               | `AppInitEventListener`, `AppShutdownEventListener` | Lifecycle logic reacts to events, not startup code    |
| Declarative validation | `MessageResponse`                                  | Constraints travel with the data                      |
| Graceful shutdown      | `HelloWorldService.cleanup()`, `application.yml`   | In-flight requests finish before the JVM exits        |
| Uniform error contract | `GlobalErrorController`, `ErrorResponse`           | Every failure renders as JSON, never an HTML page     |
| Environment profiles   | `application-{local,docker}.yml`                   | Overridden per environment, not edited in place       |

### Testing approach

35 tests at 100% coverage, in several deliberately distinct styles:

- **Pure unit tests** with no Spring context (`HelloWorldServiceTest`)
- **Mockito** with `@Mock` and static mocking (`AppMainTest`,
  `AppInitEventListenerTest`)
- **Standalone MockMvc** for the web layer without booting the app
  (`HelloWorldRestControllerTest`)
- **Full context test** proving the application actually starts (`AppTest`)
- **Nested tests** with `@Nested` and `@DisplayName` for readable output
  (`MessageResponseTest`)
- **Bean Validation testing** against a real `Validator` (`MessageResponseTest`)

## Common commands

| Command                                     | Purpose                                              |
|---------------------------------------------|------------------------------------------------------|
| `./gradlew bootRun`                         | Run locally on port 8080                             |
| `./gradlew test`                            | Run the suite; coverage report always follows        |
| `./gradlew build`                           | Format check + tests + coverage gate + all artifacts |
| `./gradlew spotlessApply`                   | Reformat sources                                     |
| `./gradlew publishToMavenLocal`             | Install to `~/.m2`                                   |
| `./gradlew release`                         | Tag, merge to `release`, bump (prefer the workflow)  |
| `./gradlew :app:dependencies --write-locks` | Regenerate the `:app` dependency lock files          |
| `gh workflow run release.yml`               | Cut a release from CI (manual trigger)               |
| `./gradlew dockerBuild`                     | Build the image (tags version + `local`)             |
| `docker compose up --build -d`              | Build and run the container image                    |
| `docker compose down`                       | Stop and remove the container                        |

## Project layout

```
spring-blueprint/
├── Dockerfile                 # multi-stage image: builder, extractor, runtime
├── docker-compose.yml         # local container up/down
├── .dockerignore              # build-context exclusions
├── settings.gradle.kts        # inclusion, repositories, version catalog
├── settings-gradle.lockfile   # lock state: version catalog resolution
├── gradle.properties          # developer identity, license, SCM, Sonar, daemon
├── BUILD.md                   # build documentation
├── llms.txt                   # machine-readable project index
├── .github/workflows/
│   ├── build-verify.yml       # CI: compile, test, check, sonar on push to main
│   └── release.yml            # manual: ./gradlew release
└── app/
    ├── build.gradle.kts       # the entire build
    ├── gradle.lockfile        # lock state: application dependencies
    ├── buildscript-gradle.lockfile  # lock state: plugin classpath
    ├── gradle.properties      # coordinates, version
    └── src/
        ├── main/resources/
        │   ├── application.yml             # defaults (quiet: root=error)
        │   ├── application-local.yml       # bootRun profile
        │   └── application-docker.yml      # container profile
        ├── main/java/com/rubensgomes/blueprint/
        │   ├── App.java                    # @SpringBootApplication entry point
        │   ├── event/                      # lifecycle listeners
        │   ├── model/response/             # response types
        │   ├── service/                    # business layer
        │   └── web/controller/             # REST layer + /error handler
        └── test/java/...                   # mirrors main
```

The single subproject is named `app` so the layout stays valid whatever the
project is called; the published artifact takes its name from the `artifactId`
property instead.

## Using this as a template

1. Clone, then update `app/gradle.properties` — `artifactId`, `group`,
   `description`, `title`, `mainClass`
2. Update `rootProject.name` in `settings.gradle.kts` to match the directory
3. Update `gradle.properties` — `scmConnection`, `scmUrl`, and the SonarCloud
   coordinates `sonar.organization`, `sonar.projectKey`, `sonar.projectName`
4. Rename the `com.rubensgomes.blueprint` package
5. Delete the `HelloWorld*` classes and their tests
6. Once your dependencies settle, regenerate the lock files with
   `./gradlew :app:dependencies --write-locks` and commit them
7. For CI, provide the two secrets the workflows expect — a `read:packages`
   PAT and a SonarCloud token — and point `distribution:` in each setup-java
   step at whatever toolchain vendor you pinned. `release.yml` additionally
   needs that PAT to have **write** access, since it pushes commits and tags

Everything else — toolchain, formatting, coverage gate, publishing, release
flow — carries over unchanged.

## Documentation

| Document                       | Contents                                                        |
|--------------------------------|----------------------------------------------------------------|
| [BUILD.md](BUILD.md)           | Every Gradle task, when it runs, how to run it, troubleshooting |
| [llms.txt](llms.txt)           | Machine-readable index for AI coding assistants                 |
| [LICENSE](LICENSE)             | MIT terms, plus AI-content and copyright-status notices         |
| [DISCLAIMER.md](DISCLAIMER.md) | General AI-generated content disclaimer                         |

## License

[MIT License](LICENSE). Author: [Rubens Gomes](https://rubensgomes.com).

This project was developed primarily with AI-assisted code generation; all
generated content was reviewed, tested, and refined by human contributors. The
[LICENSE](LICENSE) file carries the full AI-content, third-party content, and
copyright-status notices — read it rather than the SPDX tag alone.
