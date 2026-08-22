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

## Quick start

```bash
git clone https://github.com/rubensgomes-org/spring-blueprint.git
cd spring-blueprint
./gradlew bootRun
```

```bash
curl http://localhost:8080/api/v1/helloworld
# {"message":"Hello World!"}
```

No JDK setup is required — Gradle downloads the Java 25 Amazon Corretto
toolchain on first build. A `GITHUB_USER` /
`GITHUB_TOKEN` pair is needed to resolve dependencies on a fresh clone; see
[BUILD.md](BUILD.md#github-packages-credentials).

## The stack

| Layer      | Choice                                            |
|------------|---------------------------------------------------|
| Language   | Java 25 (Amazon Corretto toolchain)               |
| Framework  | Spring Boot 4.1.1 — Web MVC, Actuator, Validation |
| Build      | Gradle 9.7.0, Kotlin DSL                          |
| Versions   | Shared catalog `com.rubensgomes:gradle-catalog`   |
| Testing    | JUnit 5, Mockito, AssertJ, Spring Test            |
| Coverage   | JaCoCo, enforced at 90% line and branch           |
| Formatting | Spotless — Google Java Format, ktfmt, ktlint      |
| Analysis   | SonarCloud                                        |
| Release    | `net.researchgate.release`                        |
| Publishing | GitHub Packages                                   |

Versions are never hard-coded in the build script. Every plugin and library
resolves through the shared version catalog, and library versions come from the
Spring Boot BOM.

That pins the *declared* versions; three lock files pin the *transitive* graph
on top of it, so the same source tree resolves identically on any machine and on
any day. Regenerate them after changing a dependency or the catalog version:

```bash
./gradlew :app:dependencies --write-locks
```

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

| Pattern                | Where                                              | Why it's there                                                                                           |
|------------------------|----------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Constructor injection  | `HelloWorldRestController`                         | Dependencies are explicit and final; the class is trivially unit-testable without a Spring context       |
| Immutable value object | `MessageResponse`                                  | A `record`: implicitly final, components final, accessor/`equals`/`hashCode`/`toString` generated — safe to share across threads |
| Observer               | `AppInitEventListener`, `AppShutdownEventListener` | Lifecycle concerns react to Spring `ApplicationEvent`s instead of being wired into startup code          |
| Declarative validation | `MessageResponse`                                  | Jakarta Bean Validation constraints on the model, so validation travels with the data                    |
| Graceful shutdown      | `HelloWorldService.cleanup()`, `application.yml`   | `@PreDestroy` plus a 5s shutdown phase — in-flight requests finish before the JVM exits                  |

### Testing approach

27 tests, 100% coverage, several distinct styles deliberately shown side by
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

| Command                         | Purpose                                              |
|---------------------------------|------------------------------------------------------|
| `./gradlew bootRun`             | Run locally on port 8080                             |
| `./gradlew test`                | Run the suite; coverage report always follows        |
| `./gradlew build`               | Format check + tests + coverage gate + all artifacts |
| `./gradlew spotlessApply`       | Reformat sources                                     |
| `./gradlew publishToMavenLocal` | Install to `~/.m2`                                   |
| `./gradlew release`             | Tag, merge to `release`, bump to next snapshot       |
| `./gradlew :app:dependencies --write-locks` | Regenerate the three dependency lock files |

Full task reference, wiring diagrams, and troubleshooting:
**[BUILD.md](BUILD.md)**.

## Project layout

```
spring-blueprint/
├── settings.gradle.kts        # inclusion, repositories, version catalog
├── settings-gradle.lockfile   # lock state: version catalog resolution
├── gradle.properties          # developer identity, license, Sonar, daemon
├── BUILD.md                   # build documentation
├── llms.txt                   # machine-readable project index
└── app/
    ├── build.gradle.kts       # the entire build
    ├── gradle.lockfile        # lock state: application dependencies
    ├── buildscript-gradle.lockfile  # lock state: plugin classpath
    ├── gradle.properties      # coordinates, version, SCM
    └── src/
        ├── main/java/com/rubensgomes/blueprint/
        │   ├── App.java                    # @SpringBootApplication entry point
        │   ├── event/                      # lifecycle listeners
        │   ├── model/response/             # response types
        │   ├── service/                    # business layer
        │   └── web/controller/             # REST layer
        └── test/java/...                   # mirrors main
```

The single subproject is named `app` so the layout stays valid whatever the
project is called; the published artifact takes its name from the `artifactId`
property instead.

## Using this as a template

1. Clone, then update `app/gradle.properties` — `artifactId`, `group`,
   `description`, `title`, `mainClass`, `scmConnection`, `scmUrl`
2. Update `rootProject.name` in `settings.gradle.kts` to match the directory
3. Replace the SonarCloud placeholders in `gradle.properties`
4. Rename the `com.rubensgomes.blueprint` package
5. Delete the `HelloWorld*` classes and their tests
6. Once your dependencies settle, regenerate the lock files with
   `./gradlew :app:dependencies --write-locks` and commit them

Everything else — toolchain, formatting, coverage gate, publishing, release
flow — carries over unchanged.

## Roadmap

The laboratory half of this project. Nothing here is committed to a date:

- [ ] CI/CD workflows (GitHub Actions: build, test, publish, release)
- [ ] Containerisation (`bootBuildImage` is already available)
- [ ] Cloud deployment targets
- [ ] Persistence layer with a real domain model
- [ ] OpenAPI documentation (`springdoc-openapi` is in the catalog)
- [ ] Observability beyond Actuator defaults
- [ ] Integration test source set separate from unit tests

## Documentation

| Document             | Contents                                                        |
|----------------------|-----------------------------------------------------------------|
| [BUILD.md](BUILD.md) | Every Gradle task, when it runs, how to run it, troubleshooting |
| [llms.txt](llms.txt) | Machine-readable index for AI coding assistants                 |

## License

Apache License 2.0. Author: [Rubens Gomes](https://rubensgomes.com).
