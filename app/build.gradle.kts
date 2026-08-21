/*
 * Blueprint Gradle build script (Kotlin DSL) used by Rubens Gomes when
 * bootstrapping a new Gradle + Spring Boot Java project.
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
 *                                  authenticate against GitHub Packages.
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
    // allows this project to publish its own version catalog
    id("version-catalog")
    // net.researchgate.release: version bump + tag + branch merge release flow
    alias(libs.plugins.release)
    // org.sonarqube: static analysis upload to SonarCloud
    alias(libs.plugins.sonarqube)
    // com.diffplug.spotless: source formatting (Java, Kotlin, JSON, Gradle DSL)
    alias(libs.plugins.spotless)
    // org.springframework.boot: bootJar/bootRun and Spring Boot packaging
    alias(libs.plugins.spring.boot)
    // io.spring.dependency-management: Maven-style BOM support
    alias(libs.plugins.spring.dependency.management)
    // com.dorongold.task-tree: prints task dependency trees for debugging
    alias(libs.plugins.task.tree)
}

// ---------------------------------------------------------------------
// --------------- >>> Dependencies <<< --------------------------------
// NOTE: Versions are intentionally omitted from the coordinates below.
// They are supplied by the Spring Boot BOM imported as a platform, which
// currently resolves to Spring Boot 4.1.0 through the "libs" catalog.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/platforms.html

dependencies {
    // Import the Spring Boot 4 BOM into both the main and test graphs so
    // that managed versions apply to compile and test configurations.
    implementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.boot.bom))

    // ########## compileOnly ##########################################
    // Lombok annotations are only needed at compile time; they are not
    // required on the runtime classpath.
    compileOnly("org.projectlombok:lombok")

    // ########## implementation #######################################
    // spring boot starter dependencies
    // actuator:   health, metrics and management endpoints
    // validation: Jakarta Bean Validation (Hibernate Validator)
    // web:        Spring MVC on the default embedded Tomcat container
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // ########## developmentOnly ######################################
    // devtools enables automatic restart and live reload during local
    // development; it is excluded from the packaged bootJar.
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ########## annotationProcessor ##################################
    // runs the Lombok processor during javac
    annotationProcessor("org.projectlombok:lombok")

    // ########## runtimeOnly ##########################################
    // (none: add JDBC drivers or other runtime-only artifacts here)

    // ########## testImplementation ###################################
    // pulls in JUnit 5, AssertJ, Mockito, Spring Test and friends
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // ########## testRuntimeOnly ######################################
    // required on the JUnit Platform to discover and launch the engines
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

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
    // Compile and test against a Java 25 Amazon Corretto toolchain,
    // independent of the JDK running Gradle itself. The toolchain is
    // auto-provisioned by the foojay resolver applied in settings.gradle.kts.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.AMAZON)
    }
}

// Manifest attributes for the plain (non-executable) jar produced by the
// "jar" task, i.e. "<artifactId>-<version>-plain.jar". The Spring Boot
// plugin builds the executable jar separately via "bootJar" below.
//
// Values in project.properties come from "app/gradle.properties"; "version"
// is the standard Gradle project version, and developerName/developerId come
// from the root "gradle.properties".
//
// NOTE: "Build-Jdk"/"Created-By" describe the JVM running Gradle, which is
// not necessarily the Java 25 toolchain used to compile the classes above.
tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to project.properties["title"],
                "Implementation-Title" to project.properties["artifactId"],
                "Implementation-Version" to project.properties["version"],
                "Implementation-Vendor" to project.properties["developerName"],
                "Built-By" to project.properties["developerId"],
                "Build-Jdk" to System.getProperty("java.home"),
                "Created-By" to
                    "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})",
            ),
        )
    }
}

tasks.compileJava {
    // Reformat sources in place before compiling, so the code that gets
    // compiled is always the formatted code. This rewrites files during the
    // build; use "spotlessCheck" instead if a read-only verification is
    // preferred (for example on CI).
    dependsOn("spotlessApply")
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
}

// Run tests on the JUnit Platform (JUnit 5).
// NOTE: redundant with the tasks.test { useJUnitPlatform() } call further
// below; both configure the same "test" task.
tasks.named<Test>("test") { useJUnitPlatform() }

// ---------------------------------------------------------------------
// --------------- >>> Gradle jaCoCo Plugin <<< ------------------------
// NOTE: This section is dedicated to configuring the jacoco plugin.
// ---------------------------------------------------------------------
// https://docs.gradle.org/current/userguide/jacoco_plugin.html

tasks.jacocoTestReport {
    // the report reads test.exec, so tests must run first
    dependsOn(tasks.test)
}

// NOTE: a second configuration block for the same task; it could be merged
// into the one above.
tasks.jacocoTestReport {
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
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
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
// --------------- >>> com.diffplug.spotless Plugin <<< ----------------
// NOTE: This section is dedicated to configuring the spotless plugin.
// "spotlessApply" rewrites sources; "spotlessCheck" only verifies and is
// wired into "check". Note that compileJava depends on spotlessApply.
// ---------------------------------------------------------------------
// https://github.com/diffplug/spotless

// Apache 2.0 header injected by spotless into every Java and Kotlin source
// file. Files missing the header get it prepended; an existing header is
// replaced. Keep the year in sync with the license year used elsewhere.
val licenseHeaderText =
    """
    /*
     * Copyright 2026 Rubens Gomes
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * You may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
    """.trimIndent()

spotless {
    // Java formatting
    java {
        target("src/**/*.java")
        // Google Java Format: 2-space indent, 100-column limit
        googleJavaFormat()
        removeUnusedImports()
        licenseHeader(licenseHeaderText)
        importOrder("java", "javax", "org", "com", "")
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
    // NOTE: the target is resolved relative to THIS project directory, so it
    // matches only "app/build.gradle.kts". The root "settings.gradle.kts" is
    // not covered by this configuration.
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
// SonarQube coordinates read from the root "gradle.properties". The
// checked-in values are "@SONAR_PROJECT_KEY@"/"@SONAR_ORGANIZATION@"
// placeholders that are expected to be substituted before analysis.
// NOTE: the "as String" casts throw if a property is absent, so all three
// must be defined for the build to configure at all.
val sonarKey = project.findProperty("sonar.projectKey") as String
val sonarOrg = project.findProperty("sonar.organization") as String
val sonarUrl = project.findProperty("sonar.host.url") as String

sonar {
    properties {
        // SONAR_TOKEN must be defined as an environment variable
        property("sonar.projectKey", sonarKey)
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
// --------------- >>> org.springframework.boot Plugin <<< --------------------
// NOTE: This section is dedicated to configuring the Spring Boot plugin.
// ---------------------------------------------------------------------
// https://docs.spring.io/spring-boot/gradle-plugin/index.html

// Entry point baked into the executable jar as its Start-Class: the
// @SpringBootApplication class in "src/main/java".
springBoot { mainClass.set("com.rubensgomes.blueprint.App") }

tasks.bootJar {
    // Layered jars split dependencies from application classes so Docker
    // image layers can be cached independently.
    // layered.enabled.set(false)
    layered.enabled.set(true)
    // never package an artifact that has not passed check (tests + spotless)
    dependsOn("check")
    // NOTE: redundant. The Spring Boot plugin already writes Start-Class
    // from springBoot.mainClass above.
    manifest { attributes("Start-Class" to "com.rubensgomes.blueprint.App") }
}
