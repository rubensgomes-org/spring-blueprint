/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Rubens Gomes
 *
 * This file may contain content generated or assisted by Artificial Intelligence
 * tools and subsequently reviewed and modified by human contributors.
 * See the LICENSE file for licensing terms and additional AI disclosures.
 *
 * ---------------------------------------------------------------------
 *
 * Blueprint Gradle build script (Kotlin DSL) used by Rubens Gomes
 * in Gradle + Spring Boot Java projects.
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
 * Formatting note: this header is a plain block comment, NOT a KDoc
 * comment (a block comment whose opening delimiter ends in a second
 * asterisk). Spotless runs ktlint over this file, and the ktlint
 * "standard:kdoc" rule rejects KDoc at the top level of a Gradle script
 * because script statements are parsed as a block. Turning this header
 * back into KDoc will fail the ":app:spotlessKotlinGradleApply" task.
 * Also note that Kotlin block comments nest, so never embed a literal
 * comment-opening delimiter in a comment: it silently swallows the rest
 * of the script.
 *
 * @author [Rubens Gomes](https://rubensgomes.com)
 */

// ---------------------------------------------------------------------
// --------------- >>> Buildscript Classpath Locking <<< ---------------
// NOTE: this block locks the plugin classpath, which is a different
// graph from the application dependencies locked further below. The
// plugin VERSIONS are already pinned -- they come from the "libs"
// catalog via the alias(...) entries in the plugins block -- but the
// libraries those plugins drag in transitively are not, and they run
// inside the build. Lock state lands in "app/buildscript-gradle.lockfile".
//
// NOTE: this must stay ABOVE the plugins block. A buildscript block is
// only honoured when it precedes plugin application.
//
// NOTE: LockMode.STRICT is set again here. The lockMode configured on the
// project extension below does NOT reach the buildscript classpath: with
// only that one set, deleting "app/buildscript-gradle.lockfile" was
// verified to leave the build passing silently. The two lock modes are
// independent and both are needed.
//
// NOTE: the equivalent block in "settings.gradle.kts" was tried and
// removed. Plugins requested through the settings "plugins" block do not
// pass through the settings buildscript classpath, so it locked an empty
// configuration and wrote no lock state at all.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/dependency_locking.html#locking_buildscript_classpath

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
// NOTE: Core Gradle plugins are applied by id; third-party plugins are
// applied via alias(...) so their versions come from the shared "libs"
// version catalog rather than being hard-coded here.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/plugins.html

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
// NOTE: Versions are intentionally omitted from the coordinates below.
// They are supplied by the Spring Boot BOM imported as a platform, which
// currently resolves to Spring Boot release version defined in the plugins
// alias(libs.plugins.spring.boot) through the "libs" catalog.
//
// NOTE: the platform() import below is the ONLY dependency-management
// mechanism in this build. The legacy "io.spring.dependency-management"
// plugin is deliberately not applied: it imports the same BOM a second time
// (which showed up as a duplicated <dependencyManagement> entry in the
// generated POM) and predates Gradle native platform support.
//
// NOTE: a platform() import applies ONLY to the configuration it is
// declared on and to configurations that extend it. "compileOnly",
// "testCompileOnly" and "testRuntimeOnly" need no import of their own,
// because compileClasspath, testCompileClasspath and testRuntimeClasspath
// extend both them and the implementation buckets. "annotationProcessor",
// "testAnnotationProcessor" and "developmentOnly" extend nothing -- javac
// and the Spring Boot plugin resolve them directly -- so each imports the
// BOM itself. Without that, the versionless Lombok and devtools
// coordinates fail to resolve with "Could not find ...:" and no version.
//
// NOTE: declarations are clustered by destination -- one contiguous group
// per configuration, alphabetically, each opening with its own platform()
// import where it needs one. Gradle itself does not care about the order,
// but interleaving configurations trips SonarQube rule kotlin:S6629
// ("Dependencies should be grouped by destination") and makes it easy to
// miss that a configuration already has an entry further down.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/platforms.html

dependencies {
    // ########## annotationProcessor ##################################
    // Resolved directly by javac and extends nothing, so it imports the
    // BOM itself. Runs the Lombok processor during compilation.
    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor("org.projectlombok:lombok")

    // ########## compileOnly ##########################################
    // Lombok annotations are only needed at compile time; they are not
    // required on the runtime classpath. No platform() import needed --
    // compileClasspath extends this and the implementation bucket.
    compileOnly("org.projectlombok:lombok")

    // ########## developmentOnly ######################################
    // Resolved directly by the Spring Boot plugin for bootJar and bootRun,
    // and extends nothing, so it imports the BOM itself. devtools enables
    // automatic restart and live reload during local development; it is
    // excluded from the packaged bootJar.
    developmentOnly(platform(libs.spring.boot.bom))
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ########## implementation #######################################
    // The BOM import here is what supplies managed versions to the main
    // compile and runtime graphs.
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
    // The main source set's Lombok wiring does not extend to the test
    // source set. Without this and the testCompileOnly entry below, any
    // Lombok annotation used in "src/test" fails to compile with a
    // "cannot find symbol" error on the generated member rather than on
    // the annotation itself. Extends nothing, so it imports the BOM.
    testAnnotationProcessor(platform(libs.spring.boot.bom))
    testAnnotationProcessor("org.projectlombok:lombok")

    // ########## testCompileOnly ######################################
    // The test-source counterpart to the compileOnly entry above; see the
    // testAnnotationProcessor note for why both are required.
    testCompileOnly("org.projectlombok:lombok")

    // ########## testImplementation ###################################
    // The BOM import here supplies managed versions to the test graphs.
    // The starter pulls in JUnit 5, AssertJ, Mockito, Spring Test and
    // friends.
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // ########## testRuntimeOnly ######################################
    // required on the JUnit Platform to discover and launch the engines
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------
// --------------- >>> Dependency Locking <<< --------------------------
// NOTE: the Spring Boot BOM imported above pins the versions of the
// DECLARED coordinates only. It says nothing about the transitive closure
// those coordinates drag in, so two builds run on different days can
// resolve different transitive versions from the same source tree.
// Dependency locking records the fully resolved graph in
// "app/gradle.lockfile" and fails the build when a later resolution
// disagrees with it.
//
// NOTE: locking is applied to a fixed list rather than through
// "lockAllConfigurations()". That helper also locks the JaCoCo, Spotless,
// SonarQube and release-plugin configurations. None of those reach the
// compiled output, and each would rewrite the lockfile on every routine
// tooling bump, turning plugin upgrades into lockfile merge conflicts.
//
// NOTE: LockMode.STRICT is deliberate. Under the DEFAULT mode a missing
// lockfile is treated as "nothing to verify" and resolution silently falls
// back to whatever is newest -- precisely the unpredictability that
// locking exists to remove. STRICT turns an absent or half-merged lockfile
// into a build failure instead.
//
// The companion "settings-gradle.lockfile" in the root directory is a
// separate lock state covering the "libs" version catalog resolution. It
// needs no configuration: Gradle locks version-catalog configurations on
// its own.
//
// Regenerate after any dependency or catalog change:
//     ./gradlew :app:dependencies --write-locks
// GITHUB_USER and GITHUB_TOKEN must be set for that command; lock state
// cannot be written from the Gradle cache with --offline.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/dependency_locking.html

// Configurations whose resolved graph reaches compiled output or the test
// run. "jacocoAgent" and "jacocoAnt" are deliberately absent: they carry
// coverage tooling that is never packaged into an artifact.
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

dependencyLocking {
    lockMode.set(LockMode.STRICT)
}

configurations
    .matching { it.name in lockedConfigurations }
    .configureEach { resolutionStrategy.activateDependencyLocking() }

// ---------------------------------------------------------------------
// --------------- >>> Gradle IDEA Plugin  <<< -------------------------
// NOTE: This section is dedicated to configuring the Idea plugin.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/idea_plugin.html

idea {
    module {
        // download sources/javadoc jars so IDEA can show them inline
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// ---------------------------------------------------------------------
// --------------- >>> Gradle Java Plugin <<< --------------------------
// NOTE: This section is dedicated to configuring the Java plugin.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/java_plugin.html

java {
    // publish "-sources" and "-javadoc" jars alongside the main artifact
    withSourcesJar()
    withJavadocJar()
    // Compile and test against a Java 25 Microsoft Build of OpenJDK
    // toolchain, independent of the JDK running Gradle itself. The toolchain
    // is auto-provisioned by the foojay resolver applied in
    // settings.gradle.kts. NOTE: the Dockerfile builder stage is pinned to a
    // Microsoft base image to match this vendor; changing the vendor here
    // requires changing that base image too.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.MICROSOFT)
    }
}

// Reads a required Gradle property, declared either in "app/gradle.properties"
// or in the root "gradle.properties", and fails with an actionable message when
// it is missing.
//
// NOTE: this exists because "project.properties[...]" (i.e. Project.getProperties)
// is deprecated in Gradle 9 and is removed in Gradle 10:
// https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_get_properties
// Only that whole-map accessor is deprecated; findProperty is the supported
// replacement.
//
// NOTE: "providers.gradleProperty(name)" is NOT usable here. It resolves only
// build-level properties (the root "gradle.properties", GRADLE_USER_HOME and
// -P flags) and returns no value for anything declared in a subproject
// "gradle.properties", which is where the coordinates below live.
fun gradleProperty(name: String): String =
    project.findProperty(name)?.toString()
        ?: throw GradleException("Required property '$name' not found in gradle.properties")

// ---------------------------------------------------------------------
// --------------- >>> Resource Filtering <<< --------------------------
// NOTE: "spring.application.name" in "application.yml" is written as the
// token "@artifactId@" and replaced here with the "artifactId" property
// from "app/gradle.properties", so the module coordinate is declared
// exactly once instead of being duplicated into the runtime config.
//
// NOTE: ReplaceTokens, with its default "@...@" delimiters, is used rather
// than Gradle's expand(). expand() runs the file through the Groovy
// template engine, which evaluates "${...}" -- the same syntax Spring uses
// for its own property placeholders. A future "${SOME_ENV:default}" in
// application.yml would then break the build or be silently substituted
// away at packaging time. "@...@" cannot collide with Spring.
//
// NOTE: inputs.property is required for correctness. The task output
// depends on a Gradle property that is not otherwise one of its inputs, so
// without this the task stays up-to-date after artifactId changes and a
// stale name remains baked into "build/resources".
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/working_with_files.html#sec:filtering_files

tasks.processResources {
    val artifactId = gradleProperty("artifactId")
    inputs.property("artifactId", artifactId)
    filesMatching("application*.yml") {
        filter<org.apache.tools.ant.filters.ReplaceTokens>(
            "tokens" to mapOf("artifactId" to artifactId),
        )
    }
}

// ---------------------------------------------------------------------
// --------------- >>> Gradle Base Plugin <<< --------------------------
// NOTE: The "base" plugin (applied by "java") derives archive names from
// the Gradle project name, which is the subproject directory -- "app".
// Without this block every archive would be "app-<version>.jar" regardless
// of the artifactId, since artifactId is otherwise only read into the jar
// manifest and the published POM.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/base_plugin.html

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
// NOTE: "Build-Jdk"/"Created-By" describe the JVM running Gradle, which is
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

// NOTE: compileJava deliberately does NOT depend on spotlessApply. Rewriting
// sources before every compile means the spotlessCheck wired into "check" only
// ever inspects files that were just reformatted, so it can never fail and the
// formatting gate becomes decorative. Run "./gradlew :app:spotlessApply" to
// format sources; "check" reports violations without modifying anything.

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
}

// ---------------------------------------------------------------------
// --------------- >>> Gradle jaCoCo Plugin <<< ------------------------
// NOTE: This section is dedicated to configuring the jacoco plugin.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/jacoco_plugin.html

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

// "check" is what CI and the "bootJar" task run, so wiring the verification
// in here is what makes the threshold binding.
//
// The root project's spotlessCheck is wired in for the same reason. It lints
// the root Gradle scripts, which this project's own spotless block cannot
// reach, and CI invokes ":app:check" rather than the unqualified "check" --
// so without this dependency the root scripts would be format-checked by
// nothing. Hanging it here means bootJar, build and release inherit it too.
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn(rootProject.tasks.named("spotlessCheck"))
}

// ---------------------------------------------------------------------
// --------------- >>> Test Task Configuration <<< ---------------------
// NOTE: This configures the "test" task contributed by the Java plugin.
// The jvm-test-suite plugin is not applied to this project, so no extra
// test suites are declared here.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/java_testing.html

tasks.test {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    // Pre-load the JaCoCo java agent explicitly. Without this flag, the JVM
    // warns that a serviceability agent was loaded dynamically.
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    // report is always generated after tests run, including on test failure
    finalizedBy(tasks.jacocoTestReport)
}

// ---------------------------------------------------------------------
// --------------- >>> Gradle maven-publish Plugin <<< -----------------
// NOTE: This section is what makes the metadata in the two gradle.properties
// files load-bearing: the coordinates and scm* entries come from
// "app/gradle.properties", and the license/developer entries from the root
// "gradle.properties". Publishing components["java"] also attaches the
// "-sources" and "-javadoc" jars requested in the java { } block above.
//
// NOTE: the executable bootJar is deliberately NOT published. Consumers of a
// Maven repository expect the plain library jar; add artifact(tasks.bootJar)
// below if the runnable archive is wanted as well.
//
// NOTE: the Spring Boot plugin gives the "jar" task the "plain" classifier so
// its archive does not collide with the bootJar. Every artifact in
// components["java"] is therefore classified, which leaves the generated POM
// with <packaging>pom</packaging> and no main artifact. This is intentional
// here: Gradle consumers resolve the jar correctly through the published
// Gradle module metadata. A plain Maven consumer would not, so if this project
// ever needs to be resolvable from Maven, swap the classifiers:
//     tasks.jar { archiveClassifier.set("") }
//     tasks.bootJar { archiveClassifier.set("boot") }
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/publishing_maven.html

publishing {
    publications {
        create<MavenPublication>("maven") {
            // Without an explicit artifactId the publication is named after the
            // Gradle project directory ("app") instead of "spring-blueprint".
            groupId = project.group.toString()
            artifactId = gradleProperty("artifactId")
            version = project.version.toString()

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

// ---------------------------------------------------------------------
// --------------- >>> com.diffplug.spotless Plugin <<< ----------------
// NOTE: This section is dedicated to configuring the spotless plugin.
// "spotlessApply" rewrites sources; "spotlessCheck" only verifies and is
// wired into "check". Note that compileJava depends on spotlessApply.
// ---------------------------------------------------------------------
// https://github.com/diffplug/spotless

// SPDX MIT header injected by spotless into every Java and Kotlin source
// file under "src/**". Files missing the header get it prepended; an existing
// header is replaced. Keep the year in sync with the license year used
// elsewhere, and keep the licence itself in sync with the root LICENSE file,
// the "license"/"licenseUrl" properties in the root "gradle.properties" (they
// feed the published POM), and the OCI label in the Dockerfile.
//
// NOTE: this reaches "src/**" only. The headers on "settings.gradle.kts" and
// on this file are not managed by spotless.
val licenseHeaderText =
    """
    /*
     * SPDX-License-Identifier: MIT
     *
     * Copyright (c) 2026 Rubens Gomes
     *
     * This file may contain content generated or assisted by Artificial Intelligence
     * tools and subsequently reviewed and modified by human contributors.
     * See the LICENSE file for licensing terms and additional AI disclosures.
     */
    """.trimIndent()

spotless {
    // Java formatting
    java {
        target("src/**/*.java")
        // Google Java Format: 2-space indent, 100-column limit.
        // NOTE: this step also removes unused imports and sorts the remaining
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
    // NOTE: jackson() is deliberately NOT used here, unlike in the json block
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
    // NOTE: this target is resolved relative to THIS project directory, so it
    // covers "app/build.gradle.kts" only. Spotless refuses targets outside the
    // project dir ("All target files must be within the project dir"), so the
    // root scripts cannot be reached from here -- the root build script owns
    // them instead. See the spotless block in "build.gradle.kts".
    kotlinGradle {
        target("*.gradle.kts")
        // ktlint, driven by the root .editorconfig for fine-grained control
        ktlint().setEditorConfigPath("$rootDir/.editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ---------------------------------------------------------------------
// --------------- >>> net.researchgate.release Plugin <<< -------------
// NOTE: "gradle release" strips -SNAPSHOT, tags, bumps to the next
// snapshot and pushes. The plugin version is pinned in the root
// gradle.properties (releasePluginVersion) and resolved in
// settings.gradle.kts. This plugin is incompatible with the Gradle
// configuration cache, which is why org.gradle.configuration-cache=false
// is set in the root gradle.properties.
// ---------------------------------------------------------------------
// https://github.com/researchgate/gradle-release

release {
    with(git) {
        // branch that receives the released (non-snapshot) version
        pushReleaseVersionBranch.set("release")
        // releases may only be cut from "main"
        requireBranch.set("main")
    }
}

// ---------------------------------------------------------------------
// --------------- >>> org.sonarqube Plugin <<< ------------------------
// NOTE: This section is dedicated to configuring the sonarqube plugin.
// ---------------------------------------------------------------------
// https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/

// --------------- >>> constants <<< -----------------------------------
// SonarQube coordinates read from the root "gradle.properties".
// NOTE: the "as String" casts throw if a property is absent, so all three
// must be defined for the build to configure at all.
val sonarKey = project.findProperty("sonar.projectKey") as String
val sonarName = project.findProperty("sonar.projectName") as String
val sonarOrg = project.findProperty("sonar.organization") as String
val sonarUrl = project.findProperty("sonar.host.url") as String

sonar {
    properties {
        // SONAR_TOKEN must be defined as an environment variable
        property("sonar.projectKey", sonarKey)
        property("sonar.projectName", sonarName)
        property("sonar.organization", sonarOrg)
        property("sonar.host.url", sonarUrl)
    }
}

// Analysis runs after "check", which triggers "test"; because "test" is
// finalizedBy jacocoTestReport, the XML coverage report exists by the time
// the scanner runs, so an explicit dependency on jacocoTestReport is not
// needed:
// tasks.sonar { dependsOn("jacocoTestReport") }
tasks.sonar { dependsOn("check") }

// ---------------------------------------------------------------------
// --------------- >>> org.springframework.boot Plugin <<< -------------
// NOTE: This section is dedicated to configuring the Spring Boot plugin.
// ---------------------------------------------------------------------
// https://docs.spring.io/spring-boot/gradle-plugin/index.html

// Entry point baked into the executable jar as its Start-Class: the
// @SpringBootApplication class in "src/main/java". The FQCN lives in
// "app/gradle.properties" so it is declared exactly once.
springBoot { mainClass.set(gradleProperty("mainClass")) }

// Local runs activate the "local" profile from
// "src/main/resources/application-local.yml", which raises logging.level.root
// from the default "error" to "info".
//
// NOTE: without this, a plain "./gradlew bootRun" prints the Spring banner and
// then nothing at all -- the banner goes straight to System.out, while every
// startup message ("Starting App", "Tomcat started on port 8080", "Started App
// in Xs") is logged at INFO and discarded by the quiet default. A healthy
// startup then looks indistinguishable from a hang.
//
// NOTE: this affects bootRun ONLY. The packaged jar and any deployed run keep
// the quiet default unless something passes
// "--spring.profiles.active=local" explicitly.
tasks.bootRun { systemProperty("spring.profiles.active", "local") }

tasks.bootJar {
    // never package an artifact that has not passed check (tests + spotless)
    dependsOn("check")
    // NOTE: layered jars -- which split dependencies from application classes
    // so Docker image layers cache independently -- are enabled by default, so
    // no layered { } configuration is needed here. Set layered.enabled to false
    // to opt out.
    //
    // NOTE: Start-Class is not set here either. The Spring Boot plugin writes
    // it from springBoot.mainClass above, and the shared manifest block near
    // the top of this file supplies the remaining attributes.
}

// ---------------------------------------------------------------------
// --------------- >>> Docker Image <<< --------------------------------
// NOTE: this shells out to the "docker" CLI rather than using a Gradle
// Docker plugin. The shared catalog does expose
// alias(libs.plugins.docker.remote.api) (com.bmuschko.docker-remote-api),
// but that plugin drives the Docker Engine REST API, which uses the
// legacy builder. The Dockerfile here REQUIRES BuildKit -- the "# syntax"
// directive, "--mount=type=secret" for the GitHub Packages credentials
// and "--mount=type=cache" for the Gradle home. On the legacy builder the
// secret mounts simply do not exist, so Gradle inside the container would
// fail to resolve the version catalog with an HTTP 401.
//
// NOTE: deliberately NOT wired to "bootJar" or "build". The Dockerfile
// compiles the application inside its own builder stage, so depending on
// the host jar would run the entire suite twice -- once on the host and
// again in the container -- to produce an artifact the image never uses.
//
// NOTE: also deliberately not a dependency of "build". A container image
// is not part of the normal verification loop, and wiring it in would
// make every "./gradlew build" require a running Docker daemon.
// ---------------------------------------------------------------------
// https://docs.docker.com/build/building/secrets/

// Both tags come from the same properties the jar and the POM use, so the
// image coordinate is never a second source of truth.
val dockerImageTag = "${gradleProperty("artifactId")}:$version"
val dockerLocalTag = "${gradleProperty("artifactId")}:local"

tasks.register<Exec>("dockerBuild") {
    group = "docker"
    description = "Builds the Docker image from the Dockerfile in the root directory."

    // The build context is the repository root, not this subproject.
    workingDir = rootDir

    // Docker 23+ defaults to BuildKit, but an older client or a
    // DOCKER_BUILDKIT=0 in the environment would silently fall back to the
    // legacy builder and drop the secret mounts, so pin it.
    environment("DOCKER_BUILDKIT", "1")

    commandLine(
        "docker",
        "build",
        "--secret",
        "id=github_user,env=GITHUB_USER",
        "--secret",
        "id=github_token,env=GITHUB_TOKEN",
        "--build-arg",
        "APP_VERSION=$version",
        "--tag",
        dockerImageTag,
        "--tag",
        dockerLocalTag,
        ".",
    )

    // Checked at execution rather than configuration time so that merely
    // running "./gradlew tasks" does not fail on a machine without
    // credentials exported.
    doFirst {
        val missing =
            listOf("GITHUB_USER", "GITHUB_TOKEN").filter { System.getenv(it).isNullOrBlank() }

        if (missing.isNotEmpty()) {
            throw GradleException(
                "${missing.joinToString(" and ")} must be exported before running " +
                    "dockerBuild. The image build resolves the shared " +
                    "'com.rubensgomes:gradle-catalog' version catalog from GitHub " +
                    "Packages inside its builder stage, which always starts from a " +
                    "cold Gradle cache and therefore cannot fall back to local " +
                    "artifacts.",
            )
        }
    }
}
