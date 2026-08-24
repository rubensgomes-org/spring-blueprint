# spring-blueprint

A working Spring Boot microservice that doubles as a reference build and a
sandbox.

It exists for three reasons:

1. **A blueprint to copy.** A complete, opinionated Gradle build — toolchains,
   formatting, coverage gates, publishing, releasing, static analysis — already
   wired together and working, ready to be cloned as the starting point for a
   new project.
2. **A demonstration.** Spring Boot layering and design patterns shown in code
   small enough to read in one sitting.
3. **A laboratory.** A stable base for experimenting with new technologies,
   CI/CD workflows, and cloud deployments without risking anything real.

---

## AI-Assisted Development

This project was developed primarily using AI-assisted code generation. All
generated content was reviewed, tested, and refined by human contributors. See
the LICENSE file for additional information regarding AI-generated content.

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

> **The first image build is slow.** `bootJar` depends on `check`, so the full
> test suite and coverage gate run *inside* the builder stage. That is
> deliberate: the image cannot be built from code that has not passed
> verification. Subsequent builds hit the layer cache.

### Verify either one

```bash
curl http://localhost:8080/api/v1/helloworld
# {"message":"Hello World!"}

curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Stopping it

**Gradle** — press <kbd>Ctrl</kbd>+<kbd>C</kbd> in the terminal running
`bootRun`. That is the correct way; it shuts down gracefully, draining in-flight
requests before the JVM exits:

```
INFO ... AppShutdownEventListener  : Handling SIGTERM
INFO ... GracefulShutdown          : Commencing graceful shutdown ...
INFO ... GracefulShutdown          : Graceful shutdown complete
INFO ... HelloWorldService         : I am being terminated.
```

> **Gradle then prints `BUILD FAILED`. That is expected, not an error.** The
> forked application JVM was terminated by a signal, so it exits non-zero and
> Gradle reports the `bootRun` task as failed. The shutdown above still ran
> cleanly.

If you lost the terminal, signal the process directly — SIGTERM triggers the
same graceful path:

```bash
kill $(lsof -ti tcp:8080)
```

> **`./gradlew --stop` does not stop the application.** It stops the Gradle
> *daemon*, which is a different process; the app keeps running.

**Docker** — either of:

```bash
docker compose down      # stop and remove the container and network
docker compose stop      # stop, but keep the container for a later start
```

Both send SIGTERM to the JVM, which runs as PID 1 in the container, so the same
graceful shutdown applies. Compose allows `stop_grace_period: 15s` before
resorting to SIGKILL — comfortably more than the 5s
`spring.lifecycle.timeout-per-shutdown-phase` needs.

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
| CI         | GitHub Actions — compile, test, check, sonar      |

Versions are never hard-coded in the build script. Every plugin and library
resolves through the shared version catalog, and library versions come from the
Spring Boot BOM.

That pins the *declared* versions; three lock files pin the *transitive* graph
on top of it, so the same source tree resolves identically on any machine and on
any day.

> **Changing the catalog version requires regenerating the lock state.** Bumping
> `com.rubensgomes:gradle-catalog` in `settings.gradle.kts` on its own **breaks
> the build**. A lock file is a forcing constraint, not a checksum, so the old
> version wins and then fails to match what you declared:
>
> ```
> > Did not resolve 'com.rubensgomes:gradle-catalog:0.2.1' which is part of the dependency lock state
> > Cannot find a version of 'com.rubensgomes:gradle-catalog' that satisfies the version constraints:
>       'com.rubensgomes:gradle-catalog:{strictly 0.2.1}' because of the following reason:
>       Dependency version enforced by Dependency Locking
> ```
>
> The fix is always the same — regenerate, then commit `settings.gradle.kts` and
> the lock files **together**. Splitting them across commits reproduces this
> failure for everyone else.

Regenerate after changing a dependency, the set of locked configurations, or the
catalog version:

```bash
./gradlew :app:dependencies --write-locks
```

This needs `GITHUB_USER` and `GITHUB_TOKEN` exported: lock state is written from
a real resolution against GitHub Packages and cannot be produced `--offline`
from a warm cache. If the build instead reports
`Resource missing ... repo.maven.apache.org/.../gradle-catalog-<version>.pom`,
those variables are unset — the catalog is not on Maven Central, so resolution
fell through to it and 404'd.

Bumping the catalog does not necessarily change anything downstream. If the
catalog declares the same versions this project already uses, only
`settings-gradle.lockfile` changes and the other two come back byte-identical.

Locking runs in strict mode — a missing lock file fails the build rather than
quietly resolving whatever is newest. Details in
[BUILD.md](BUILD.md#dependency-locking--reproducible-resolution).

## What it demonstrates

### Layered architecture

```
web/controller  →  service  →  model/response
HelloWorld-         HelloWorld-   MessageResponse
RestController      Service
```

The controller owns HTTP concerns only — routing, status codes, content
negotiation — and delegates every business decision to the service layer. The
service returns a model type rather than a raw string, so the response contract
survives the business logic changing underneath it. Swapping in a real domain
layer means editing one class.

### Patterns in the code

| Pattern                | Where                                              | Why it's there                                                                                                                   |
|------------------------|----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Constructor injection  | `HelloWorldRestController`                         | Dependencies are explicit and final; the class is trivially unit-testable without a Spring context                               |
| Immutable value object | `MessageResponse`                                  | A `record`: implicitly final, components final, accessor/`equals`/`hashCode`/`toString` generated — safe to share across threads |
| Observer               | `AppInitEventListener`, `AppShutdownEventListener` | Lifecycle concerns react to Spring `ApplicationEvent`s instead of being wired into startup code                                  |
| Declarative validation | `MessageResponse`                                  | Jakarta Bean Validation constraints on the model, so validation travels with the data                                            |
| Graceful shutdown      | `HelloWorldService.cleanup()`, `application.yml`   | `@PreDestroy` plus a 5s shutdown phase — in-flight requests finish before the JVM exits                                          |
| Uniform error contract | `GlobalErrorController`, `ErrorResponse`           | One `/error` mapping renders every failure as JSON, so a REST client never receives an HTML error page                           |
| Environment profiles   | `application-{local,docker}.yml`                   | The quiet production default is overridden per environment rather than edited in place                                           |

### Testing approach

35 tests, 100% coverage, several distinct styles deliberately shown side by
side:

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
| `./gradlew release`                         | Tag, merge to `release`, bump to next snapshot       |
| `./gradlew :app:dependencies --write-locks` | Regenerate the three dependency lock files           |
| `./gradlew dockerBuild`                     | Build the image (tags version + `local`)             |
| `docker compose up --build -d`              | Build and run the container image                    |
| `docker compose down`                       | Stop and remove the container                        |

Full task reference, wiring diagrams, and troubleshooting:
**[BUILD.md](BUILD.md)**.

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
│   └── build-verify.yml       # CI: compile, test, check, sonar on push to main
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
7. For CI, provide the two secrets `.github/workflows/build-verify.yml`
   expects — a `read:packages` PAT and a SonarCloud token — and point
   `distribution:` in its setup-java step at whatever toolchain vendor you
   pinned

Everything else — toolchain, formatting, coverage gate, publishing, release
flow — carries over unchanged.

## Roadmap

The laboratory half of this project. Nothing here is committed to a date:

- [x] CI verification (GitHub Actions: compile, test, check, sonar on push to
  `main`)
- [ ] CD workflows — publish, release, and deploy from CI
- [x] Containerisation — multi-stage `Dockerfile` + `docker-compose.yml`
- [ ] Cloud deployment targets
- [ ] Persistence layer with a real domain model
- [ ] OpenAPI documentation (`springdoc-openapi` is in the catalog)
- [ ] Observability beyond Actuator defaults
- [ ] Integration test source set separate from unit tests

## Documentation

| Document                       | Contents                                                        |
|--------------------------------|-----------------------------------------------------------------|
| [BUILD.md](BUILD.md)           | Every Gradle task, when it runs, how to run it, troubleshooting |
| [llms.txt](llms.txt)           | Machine-readable index for AI coding assistants                 |
| [LICENSE](LICENSE)             | MIT terms, plus AI-content and copyright-status notices         |
| [DISCLAIMER.md](DISCLAIMER.md) | General AI-generated content disclaimer                         |

## License

[MIT License](LICENSE). Author: [Rubens Gomes](https://rubensgomes.com).

Source files carry an `SPDX-License-Identifier: MIT` header, injected and
verified by Spotless. The [LICENSE](LICENSE) file also carries the project's
AI-generated content, third-party content, and copyright-status notices — read
it rather than the SPDX tag alone.
