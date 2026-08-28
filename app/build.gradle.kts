/*
 * SPDX-License-Identifier: MIT
 *
 * ---------------------------------------------------------------------
 *
 * Blueprint Gradle build script (Kotlin DSL)
 *
 * This script configures the ":app" subproject, which is declared in the
 * root "settings.gradle.kts" via include("app").
 *
 * Where configuration comes from:
 *
 *  - "app/gradle.properties"  -> project coordinates and metadata
 *                                (group, version, artifactId, title,
 *                                description, scm*, mainClass).
 *  - "gradle.properties"      -> developer identity, license, SonarQube
 *                                properties, GitHub Packages repo URL and
 *                                Gradle daemon settings.
 *  - "libs" version catalog   -> NOT a local "gradle/libs.versions.toml".
 *                                It is resolved from the published catalog
 *                                "com.rubensgomes:gradle-catalog", wired up
 *                                in "settings.gradle.kts".
 *  - ".editorconfig"          -> ktlint formatting rules (see spotless).
 *
 * Environment variables expected by a full build/release:
 *
 *  - GITHUB_USER / GITHUB_TOKEN -> read in "settings.gradle.kts" to
 *                                  authenticate against GitHub Packages,
 *                                  and again by the publishing block below
 *                                  when uploading this artifact.
 *  - SONAR_TOKEN                -> required by the "sonar" task.
 *
 * @author [Rubens Gomes](https://rubensgomes.com)
 */

// ---------------------------------------------------------------------
// --------------- >>> Buildscript Classpath Locking <<< ---------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/dependency_locking.html#locking_buildscript_classpath

// NOTE: this block locks the plugin classpath, which is a different
// graph from the application dependencies locked further below. The
// plugin VERSIONS are already pinned -- they come from the "libs"
// catalog via the alias(...) entries in the plugins block -- but the
// libraries those plugins drag in transitively are not, and they run
// inside the build. Lock state lands in "app/buildscript-gradle.lockfile".
//
// This must stay ABOVE the plugins block. A buildscript block is only
// honored when it precedes plugin application.
//
// LockMode.STRICT is set again here. The lockMode configured on the
// project extension below does NOT reach the buildscript classpath: with
// only that one set, deleting "app/buildscript-gradle.lockfile" was
// verified to leave the build passing silently. The two lock modes are
// independent and both are needed.

buildscript {
    dependencyLocking {
        lockMode.set(LockMode.STRICT)
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

// ---------------------------------------------------------------------
// --------------- >>> Gradle Plugins <<< ------------------------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/plugins.html

// NOTE: Core Gradle plugins are applied by id; third-party plugins are
// applied via alias(...) so their versions come from the shared "libs"
// version catalog rather than being hard-coded here.

plugins {
    // generates IntelliJ IDEA module/project metadata
    id("idea")
    // code coverage measurement and reporting
    id("jacoco")
    // Java compilation, testing, packaging (also applies "base")
    id("java")
    // publishes the jar, its POM and the sources/javadoc jars to a Maven repo
    id("maven-publish")
    // net.researchgate.release: version bump + tag + branch merge release flow
    alias(libs.plugins.release)
    // org.sonarqube: static analysis upload to SonarCloud
    alias(libs.plugins.sonarqube)
    // com.diffplug.spotless: source formatting (Java, Kotlin, JSON, Gradle DSL)
    alias(libs.plugins.spotless)
    // org.springframework.boot: bootJar/bootRun and Spring Boot packaging
    alias(libs.plugins.spring.boot)
    // com.dorongold.task-tree: prints task dependency trees for debugging
    alias(libs.plugins.task.tree)
}

// ---------------------------------------------------------------------
// --------------- >>> Dependencies <<< --------------------------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/platforms.html

// NOTE: Versions are intentionally omitted from the coordinates below.
// They are supplied by the Spring Boot BOM imported as a platform, which
// currently resolves to Spring Boot release version defined in the plugins
// alias(libs.plugins.spring.boot) through the "libs" catalog.

dependencies {
    // ########## annotationProcessor ##################################
    // The annotationProcessor configuration is a separate dependency graph
    // used by javac during compilation of files in src/main/java for
    // annotation processors. Because it does not inherit dependency
    // management from implementation or compileClasspath, the Spring Boot
    // BOM is imported here explicitly so Lombok's version can be managed by
    // the BOM. Without this and the testCompileOnly entry below, any Lombok
    // annotation used in "src/main" fails to compile with a
    //  "cannot find symbol" error on the generated member rather than on
    //  the annotation itself.
    // 1. Import the Spring Boot BOM into the annotationProcessor configuration.
    // 2. Add Lombok to the annotationProcessor configuration.
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor("org.projectlombok:lombok")

    // ########## compileOnly ##########################################
    // Lombok annotations are only needed at compile time; they are not
    // required on the runtime classpath. During runtime the generated code
    // is already contained and no longer needs the lombok library package.
    compileOnly("org.projectlombok:lombok")

    // ########## developmentOnly ######################################
    // The "developmentOnly" is a Gradle configuration created by the Spring
    // Boot Gradle plugin specifically for dependencies that should be
    // available while developing the application but should not be included
    // in a production deployment.
    // 1. Import Spring Boot BOM to be used by the Spring Boot devtools
    // 2. "devtools" enables automatic restart and live reload during local
    //    development
    developmentOnly(platform(libs.spring.boot.bom))
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ########## implementation #######################################
    // The platform BOM import here is what supplies managed versions to
    // the main compile and runtime graphs.
    // actuator:   health, metrics and management endpoints
    // validation: Jakarta Bean Validation (Hibernate Validator)
    // web:        Spring MVC on the default embedded Tomcat container
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // ########## runtimeOnly ##########################################
    // (none: add JDBC drivers or other runtime-only artifacts here)

    // ########## testAnnotationProcessor ##############################
    // The testAnnotationProcessor configuration is a separate dependency graph
    // used by javac during compilation of files in src/test/java for
    // annotation processors. Because it does not inherit dependency
    // management from implementation or compileClasspath, the Spring Boot
    // BOM is imported here explicitly so Lombok's version can be managed by
    // the BOM. Without this and the testCompileOnly entry below, any Lombok
    // annotation used in "src/test" fails to compile with a
    // "cannot find symbol" error on the generated member rather than on
    // the annotation itself.
    // 1. Import the Spring Boot BOM into the testAnnotationProcessor configuration.
    // 2. Add Lombok to the testAnnotationProcessor configuration.
    testAnnotationProcessor(platform(libs.spring.boot.bom))
    testAnnotationProcessor("org.projectlombok:lombok")

    // ########## testCompileOnly ######################################
    // The test-source counterpart to the compileOnly entry above; see the
    // testAnnotationProcessor note for why both are required.
    testCompileOnly("org.projectlombok:lombok")

    // ########## testImplementation ###################################
    // The platform BOM import here is what supplies managed versions to
    // the main compile and runtime graphs.
    // The starter pulls in JUnit 5, AssertJ, Mockito, Spring Test and
    // friends.
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // ########## testRuntimeOnly ######################################
    // required on the JUnit Platform to discover and launch the engines
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> Dependency Locking <<< --------------------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/dependency_locking.html

// NOTE: The companion "settings-gradle.lockfile" in the root directory is
// a separate lock state covering the "libs" version catalog resolution. It
// needs no configuration: Gradle locks version-catalog configurations on
// its own.
//
// The Spring Boot BOM imported above pins the versions of the DECLARED
// coordinates only. It says nothing about the transitive closure
// those coordinates drag in, so two builds run on different days can
// resolve different transitive versions from the same source tree.
// Dependency locking records the fully resolved graph in
// "app/gradle.lockfile" and fails the build when a later resolution
// disagrees with it.
//
// Regenerate after any dependency or catalog change:
//     ./gradlew :app:dependencies --write-locks
// GITHUB_USER and GITHUB_TOKEN must be set for that command; lock state
// cannot be written from the Gradle cache with --offline.

val lockedConfigurations =
    setOf(
        "annotationProcessor",
        "compileClasspath",
        "developmentOnly",
        "runtimeClasspath",
        "testAnnotationProcessor",
        "testCompileClasspath",
        "testRuntimeClasspath",
    )

// LockMode.STRICT is deliberate. Under the DEFAULT mode a missing
// lockfile is treated as "nothing to verify" and resolution silently falls
// back to whatever is newest -- precisely the unpredictability that
// locking exists to remove. STRICT turns an absent or half-merged lockfile
// into a build failure instead.
dependencyLocking {
    lockMode.set(LockMode.STRICT)
}

configurations
    .matching { it.name in lockedConfigurations }
    .configureEach { resolutionStrategy.activateDependencyLocking() }

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> Gradle IDEA Plugin  <<< -------------------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/idea_plugin.html

// NOTE: This section is dedicated to configuring the Idea plugin.

idea {
    module {
        // download sources/javadoc jars so IDEA can show them inline
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> Gradle Java Plugin <<< --------------------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/java_plugin.html

// NOTE: This section is dedicated to configuring the Java plugin.

java {
    // publish "-sources" and "-javadoc" jars alongside the main artifact
    withSourcesJar()
    withJavadocJar()
    // Compile and test against a Java 25 Microsoft Build of OpenJDK
    // toolchain, independent of the JDK running Gradle itself. The toolchain
    // is auto-provisioned by the foojay resolver applied in
    // settings.gradle.kts.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.MICROSOFT)
    }
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> gradleProperty Function <<< ---------------------
// ---------------------------------------------------------------------

// NOTE: this exists because "project.properties[...]" (i.e. Project.getProperties)
// is deprecated in Gradle 9 and is removed in Gradle 10:
// https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_get_properties
// Only that whole-map accessor is deprecated; findProperty is the supported
// replacement.
//
// Reads a required Gradle property, declared either in "app/gradle.properties"
// or in the root "gradle.properties", and fails with an actionable message when
// it is missing.

fun gradleProperty(name: String): String =
    project.findProperty(name)?.toString()
        ?: throw GradleException("Required property '$name' not found in gradle.properties")

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> Resource Filtering <<< --------------------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/working_with_files.html#sec:filtering_files

// NOTE: "spring.application.name" in "application.yml" is written as the
// token "@artifactId@" and replaced here with the "artifactId" property
// from "app/gradle.properties", so the module coordinate is declared
// exactly once instead of being duplicated into the runtime config.
//
// ReplaceTokens, with its default "@...@" delimiters, is used rather
// than Gradle's expand(). expand() runs the file through the Groovy
// template engine, which evaluates "${...}" -- the same syntax Spring uses
// for its own property placeholders. A future "${SOME_ENV:default}" in
// application.yml would then break the build or be silently substituted
// away at packaging time. "@...@" cannot collide with Spring.

tasks.processResources {
    val artifactId = gradleProperty("artifactId")
    inputs.property("artifactId", artifactId)
    filesMatching("application*.yml") {
        filter<org.apache.tools.ant.filters.ReplaceTokens>(
            "tokens" to mapOf("artifactId" to artifactId),
        )
    }
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> Gradle Base Plugin <<< --------------------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/base_plugin.html

// NOTE: The "base" plugin (applied by "java") derives archive names from
// the Gradle project name, which is the subproject directory -- "app".
// Without this block every archive would be "app-<version>.jar" regardless
// of the artifactId, since artifactId is otherwise only read into the jar
// manifest and the published POM.

base { archivesName.set(gradleProperty("artifactId")) }

// Manifest attributes applied to EVERY jar this project produces: the plain
// jar, the executable bootJar, and the sources/javadoc jars. Configuring
// "tasks.jar" alone would leave the bootJar -- the artifact that actually gets
// deployed -- with only the defaults the Spring Boot plugin writes.
//
// Values come from "app/gradle.properties"; "version" is the standard Gradle
// project version, and developerName/developerId come from the root
// "gradle.properties".
//
// "Build-Jdk"/"Created-By" describe the JVM running Gradle, which is
// not necessarily the Java 25 toolchain used to compile the classes above.
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to gradleProperty("title"),
                "Implementation-Title" to gradleProperty("artifactId"),
                "Implementation-Version" to project.version.toString(),
                "Implementation-Vendor" to gradleProperty("developerName"),
                "Built-By" to gradleProperty("developerId"),
                "Build-Jdk" to System.getProperty("java.version"),
                "Created-By" to
                    "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})",
            ),
        )
    }
}

tasks.javadoc {
    // Restrict the Javadoc input to Java sources only. Javadoc cannot parse
    // Kotlin, so .kt files are excluded and the source set is narrowed to
    // allJava.
    exclude("**/*.kt")
    source = sourceSets.main.get().allJava

    if (JavaVersion.current().isJava9Compatible) {
        // emit HTML5 markup rather than the legacy HTML4 output
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }

    // Run every doclint check EXCEPT "missing". CLAUDE.md forbids Javadoc on
    // private members, constructors and obvious methods, which doclint's
    // missing-comment check reports as warnings -- following the documentation
    // standard would otherwise mean a permanently noisy build. The checks that
    // catch real defects (broken @link targets, malformed HTML, bad @param
    // names) all stay on.
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> Gradle jaCoCo Plugin <<< ------------------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/jacoco_plugin.html

// NOTE: This section is dedicated to configuring the jacoco plugin.

tasks.jacocoTestReport {
    // the report reads test.exec, so tests must run first
    dependsOn(tasks.test)

    reports {
        // XML is what the SonarQube scanner consumes
        xml.required = true
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
}

// Enforce a minimum coverage level so the suite cannot silently rot. The
// rule is evaluated over the whole bundle (all classes in the "app"
// subproject), not per class, so a single small uncovered helper does not
// fail the build on its own.
//
// LINE guards how much code the tests execute; BRANCH guards that the
// conditions on those lines are actually exercised in both directions,
// which LINE alone does not catch.
//
// To exempt generated or configuration classes, add an "excludes" list of
// class name patterns inside the element below, for example:
// excludes = listOf("com.rubensgomes.blueprint.config.*")
tasks.jacocoTestCoverageVerification {
    // depends on the report rather than on "test" directly, so that the HTML
    // and XML reports are already written when a violation fails the build.
    dependsOn(tasks.jacocoTestReport)

    violationRules {
        rule {
            element = "BUNDLE"

            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }

            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> Test Task Configuration <<< ---------------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/java_testing.html

// NOTE: This configures the "test" task contributed by the Java plugin.
// The jvm-test-suite plugin is not applied to this project, so no extra
// test suites are declared here.

tasks.test {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    // Pre-load the JaCoCo java agent explicitly. Without this flag, the JVM
    // warns that a serviceability agent was loaded dynamically.
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    // report is always generated after tests run, including on test failure
    finalizedBy(tasks.jacocoTestReport)
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> Gradle maven-publish Plugin <<< -----------------
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/publishing_maven.html

// NOTE: This section contains properties defined in the two gradle.properties
// files: "app/gradle.properties" and the root "gradle.properties"
//
// The executable bootJar is deliberately NOT published. Consumers of a
// Maven repository expect the plain library jar; add artifact(tasks.bootJar)
// below if the runnable archive is wanted as well.
//
// The Spring Boot plugin gives the "jar" task the "plain" classifier so
// its archive does not collide with the bootJar.
//
// This publishing does not contain a normal, unclassified JAR. All published
// JARs have classifiers such as sources, javadoc, plain, and sources.
// "-javadoc", "-plain", and "-sources". Therefore, the generated POM ends up
// with <packaging>pom</packaging> and no main artifact.
//

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            // Without an explicit artifactId the publication is named after the
            // Gradle project directory ("app") instead of "spring-blueprint".
            artifactId = gradleProperty("artifactId")
            version = project.version.toString()

            // Publishing components["java"] also attaches the"-sources" and
            // "-javadoc" jars requested in the java { } block above.
            from(components["java"])

            pom {
                name.set(gradleProperty("title"))
                description.set(gradleProperty("description"))
                url.set(gradleProperty("scmUrl"))

                licenses {
                    license {
                        name.set(gradleProperty("license"))
                        url.set(gradleProperty("licenseUrl"))
                    }
                }

                developers {
                    developer {
                        id.set(gradleProperty("developerId"))
                        name.set(gradleProperty("developerName"))
                        email.set(gradleProperty("developerEmail"))
                    }
                }

                scm {
                    connection.set(gradleProperty("scmConnection"))
                    developerConnection.set(gradleProperty("scmConnection"))
                    url.set(gradleProperty("scmUrl"))
                }
            }
        }
    }

    repositories {
        // Same GitHub Packages repo and credentials that "settings.gradle.kts"
        // uses for reading, here used for writing. When GITHUB_USER/GITHUB_TOKEN
        // are unset the upload fails with a 401; "publishToMavenLocal" needs
        // neither the URL nor the credentials.
        val mavenRepoPackages = project.findProperty("mavenRepoPackages")?.toString()

        if (!mavenRepoPackages.isNullOrBlank()) {
            maven {
                name = "GitHubPackages"
                setUrl(mavenRepoPackages)
                credentials {
                    username = providers.environmentVariable("GITHUB_USER").orNull
                    password = providers.environmentVariable("GITHUB_TOKEN").orNull
                }
            }
        }
    }
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> com.diffplug.spotless Plugin <<< ----------------
// ---------------------------------------------------------------------
// https://github.com/diffplug/spotless

// NOTE: This section is dedicated to configuring the spotless plugin.
// "spotlessApply" rewrites sources; "spotlessCheck" only verifies and is
// wired into "check". Note that compileJava depends on spotlessApply.
//
// SPDX MIT header injected by spotless into every Java and Kotlin source
// file under "src/**". Files missing the header get it prepended; an existing
// header is replaced. Keep the year in sync with the license year used
// elsewhere, and keep the licence itself in sync with the root LICENSE file,
// the "license"/"licenseUrl" properties in the root "gradle.properties" (they
// feed the published POM), and the OCI label in the Dockerfile.
//
// This reaches "src/**" only. The headers on "settings.gradle.kts" and
// on this file are not managed by spotless.

val licenseHeaderText =
    """
    /*
     * SPDX-License-Identifier: MIT
     */
    """.trimIndent()

spotless {
    // Java formatting
    java {
        target("src/**/*.java")
        // Google Java Format: 2-space indent, 100-column limit.
        // This step also removes unused imports and sorts the remaining
        // ones into Google's canonical order, so no separate
        // removeUnusedImports() or importOrder() step is configured. Adding an
        // importOrder() step here would run after this one and silently
        // override that ordering.
        googleJavaFormat()
        licenseHeader(licenseHeaderText)
        trimTrailingWhitespace()
        endWithNewline()
    }

    // Kotlin formatting
    kotlin {
        target("src/**/*.kt")
        // ktfmt (Facebook) rather than ktlint for .kt sources
        ktfmt()
        licenseHeader(licenseHeaderText)
        trimTrailingWhitespace()
        endWithNewline()
    }

    // JSON formatting
    json {
        target("src/**/*.json")
        jackson()
    }

    // YAML formatting.
    //
    // jackson() is deliberately NOT used here, unlike in the json block
    // above. The Jackson step reads the document into an object model and
    // writes it back out, which silently deletes every comment in the file.
    // The Spring "application.yml" files are heavily commented, so only
    // whitespace-level steps are applied. Use prettier() instead if a full
    // YAML reformatter is ever wanted; it keeps comments, but it requires a
    // local Node.js installation.
    yaml {
        target("src/**/*.yaml", "src/**/*.yml")
        // YAML is indentation-sensitive and tabs are illegal in it, so any
        // leading tab is converted to the 2 spaces used across these files.
        leadingTabsToSpaces(2)
        trimTrailingWhitespace()
        endWithNewline()
    }

    // Kotlin Gradle DSL formatting.
    // This target is resolved relative to THIS project directory, so it
    // covers "app/build.gradle.kts" only. Spotless refuses targets outside the
    // project dir ("All target files must be within the project dir"), so
    // "settings.gradle.kts" cannot be reached from here however this target is
    // written. There is no root build script to cover it either, so that file
    // is format-checked by nothing and is maintained by hand.
    kotlinGradle {
        target("*.gradle.kts")
        // ktlint, driven by the root .editorconfig for fine-grained control
        ktlint().setEditorConfigPath("$rootDir/.editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> net.researchgate.release Plugin <<< -------------
// ---------------------------------------------------------------------
// https://github.com/researchgate/gradle-release

// NOTE: "gradle release" strips -SNAPSHOT, tags, updates version in
// gradle.properties to the next snapshot and pushes the current release
// version.
//
// The "release" plugin version is pinned in the root gradle.properties
// (releasePluginVersion) and resolved in settings.gradle.kts. This plugin is
// incompatible with the Gradle configuration cache, which is why
// org.gradle.configuration-cache=false is set in the root gradle.properties.

release {
    with(git) {
        // releases may only be cut from "main"
        requireBranch.set("main")

        // NOTE: pushReleaseVersionBranch is deliberately NOT set. Setting it
        // makes the plugin run a plain "git checkout <branch>" in its
        // checkoutMergeToReleaseBranch step -- not "checkout -b" -- so the
        // branch has to exist already, and this repository is single-trunk:
        // "main" is the only branch. The release is identified by its tag.
    }
}

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> org.sonarqube Plugin <<< ------------------------
// ---------------------------------------------------------------------
// https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/

// NOTE: This section is dedicated to configuring the sonarqube plugin.
// SonarQube coordinates read from the root "gradle.properties".
// NOTE: the "as String" casts throw if a property is absent, so all three
// must be defined for the build to configure at all.
val sonarKey = project.findProperty("sonar.projectKey") as String
val sonarName = project.findProperty("sonar.projectName") as String
val sonarOrg = project.findProperty("sonar.organization") as String
val sonarUrl = project.findProperty("sonar.host.url") as String
val sonarWait = project.findProperty("sonar.qualitygate.wait") as String

sonar {
    properties {
        // SONAR_TOKEN must be defined as an environment variable
        property("sonar.projectKey", sonarKey)
        property("sonar.projectName", sonarName)
        property("sonar.projectVersion", project.version.toString())
        property("sonar.organization", sonarOrg)
        property("sonar.host.url", sonarUrl)
        property("sonar.qualitygate.wait", sonarWait)
    }
}

// Sonar static analysis should run after "check", which triggers "test";
// because "test" is finalizedBy jacocoTestReport, the XML coverage report
// exists by the time the scanner runs
tasks.sonar { dependsOn("check") }

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> org.springframework.boot Plugin <<< -------------
// ---------------------------------------------------------------------
// https://docs.spring.io/spring-boot/gradle-plugin/index.html

// NOTE: This section is dedicated to configuring the Spring Boot plugin.
// Entry point baked into the executable jar as its Start-Class: the
// @SpringBootApplication class in "src/main/java". The FQCN lives in
// "app/gradle.properties" so it is declared exactly once.
springBoot { mainClass.set(gradleProperty("mainClass")) }

// This affects bootRun ONLY. The packaged jar and any deployed run keep
// the quiet default unless something passes
// "--spring.profiles.active=local" explicitly.
//
// Local Spring Boot bootRun runs activate the "local" profile from
// "src/main/resources/application-local.yml"
tasks.bootRun { systemProperty("spring.profiles.active", "local") }

tasks.bootJar {
    // never package an artifact that has not passed check (tests + spotless)
    dependsOn("check")
    // NOTE: layered jars -- which split dependencies from application classes
    // so Docker image layers cache independently -- are enabled by default, so
    // no layered { } configuration is needed here. Set layered.enabled to false
    // to opt out.
    //
    // Start-Class is not set here either. The Spring Boot plugin writes
    // it from springBoot.mainClass above, and the shared manifest block near
    // the top of this file supplies the remaining attributes.
}

// Stable-named duplicate of the layered bootJar.
// NOTE: A copy of the Spring Boot multi-layered jar is being placed in
// the gradle target build folder.  This was done to facilitate the use
// of that multi-layer package file from within the Dockerfile.  The Docker
// build needs a filename that does not change with the project version;
// the versioned archive is left in place for publishing and release tagging.
val applicationJar =
    tasks.register<Copy>("applicationJar") {
        val artifactId = gradleProperty("artifactId")
        description = "Copies the Spring Boot multi-layer jar to $artifactId-spring-boot.jar"
        from(tasks.bootJar)
        into(layout.buildDirectory.dir("libs"))
        rename { "$artifactId-spring-boot.jar" }
    }

// "docker compose" reads APP_VERSION from a ".env" file in the repository
// root and forwards it to the Dockerfile's "ARG APP_VERSION", which becomes
// the org.opencontainers.image.version label. Generating that file here keeps
// "app/gradle.properties" the single place the version is declared: the
// release plugin bumps it there, and the next build propagates it into the
// image. Without this the label silently reads "unknown".
//
// The generated file is NOT committed -- see ".gitignore".
val generateDotEnv =
    tasks.register("generateDotEnv") {
        description = "Writes APP_VERSION to the root .env read by docker compose."
        // Both values are resolved at configuration time so the doLast action
        // below captures plain data rather than a reference to "project".
        val appVersion = project.version.toString()
        val envFile = rootProject.layout.projectDirectory.file(".env")
        inputs.property("appVersion", appVersion)
        outputs.file(envFile)
        doLast { envFile.asFile.writeText("APP_VERSION=$appVersion\n") }
    }

// Ensure that the build task makes a copy of the Spring Boot multi-layer jar
// and refreshes the ".env" consumed by docker compose.
tasks.build { dependsOn(applicationJar, generateDotEnv) }

// *********************************************************************
// ---------------------------------------------------------------------
// --------------- >>> Check (Lifecyle) Task Configuration <<< ---------
// ---------------------------------------------------------------------
// NOTE: This section is dedicated to configuring the "check" lifecyle plugin.
// Attach coverage verification to Gradle's standard `check` verification
// lifecycle. This configuration will automatically enforce the JaCoCo
// coverage thresholds. If coverage verification fails, `check` and therefore
// `build` will fail as well.
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    description = "Runs checks (including tests and coverage verification)."
}
