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
 * Root build script.
 *
 * This project deliberately keeps the entire application build in
 * "app/build.gradle.kts". This root script exists for ONE reason: to format
 * and lint the Gradle scripts that live at the repository root.
 *
 * Spotless refuses any target outside its own project directory --
 * "All target files must be within the project dir" -- so the spotless block
 * in "app/build.gradle.kts" can never reach "settings.gradle.kts" no matter
 * how its target is written. Only the root project can. Without this file,
 * the root scripts are linted by nothing, which is how a stale Apache-2.0
 * licence header once survived there unnoticed.
 *
 * Add nothing else here. Application configuration belongs in
 * "app/build.gradle.kts".
 *
 * @author [Rubens Gomes](https://rubensgomes.com)
 */

// ---------------------------------------------------------------------
// --------------- >>> Buildscript Classpath Locking <<< ---------------
// NOTE: applying a plugin here creates a SECOND buildscript classpath --
// this one, separate from the ":app" classpath locked in
// "app/buildscript-gradle.lockfile". Left unlocked it would be the only
// unlocked dependency graph in the build, so it is locked to the same
// LockMode.STRICT. Lock state lands in "buildscript-gradle.lockfile" at
// the repository root.
//
// NOTE: this must stay ABOVE the plugins block. A buildscript block is
// only honoured when it precedes plugin application.
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

plugins {
    // org.sonarqube: static analysis upload to SonarCloud. Applied HERE, at
    // the root, and deliberately not in "app/build.gradle.kts": the scanner
    // pins "sonar.projectBaseDir" to the project the plugin is applied to,
    // so applying it to ":app" made "app/" the whole analysed world and left
    // ".github/workflows", the root scripts, the Dockerfile and
    // "docker-compose.yml" outside every scan. ":app" still contributes its
    // own sources -- it participates as a Sonar module.
    alias(libs.plugins.sonarqube)
    // com.diffplug.spotless: formatting for the ROOT Gradle scripts only
    alias(libs.plugins.spotless)
}

// ---------------------------------------------------------------------
// --------------- >>> com.diffplug.spotless Plugin <<< ----------------
// NOTE: this covers the root scripts ONLY -- "settings.gradle.kts" and
// this file. "app/build.gradle.kts" is handled by the spotless block in
// that file, because a target is always resolved relative to the project
// that declares it.
// ---------------------------------------------------------------------
// https://github.com/diffplug/spotless

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        // ktlint, driven by the root .editorconfig, matching the ":app" setup
        ktlint().setEditorConfigPath("$rootDir/.editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ---------------------------------------------------------------------
// --------------- >>> org.sonarqube Plugin <<< ------------------------
// NOTE: This section is dedicated to configuring the sonarqube plugin.
// It lives at the root, not in "app/build.gradle.kts", so that
// "sonar.projectBaseDir" resolves to the repository root and the
// repository-level files can be indexed at all.
// ---------------------------------------------------------------------
// https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/scanners/sonarscanner-for-gradle/

// --------------- >>> constants <<< -----------------------------------
// SonarQube coordinates read from the root "gradle.properties".
// NOTE: the "as String" casts throw if a property is absent, so all four
// must be defined for the build to configure at all.
val sonarKey = project.findProperty("sonar.projectKey") as String
val sonarName = project.findProperty("sonar.projectName") as String
val sonarOrg = project.findProperty("sonar.organization") as String
val sonarUrl = project.findProperty("sonar.host.url") as String

// Repository-level files to analyse. These belong to the ROOT Sonar
// module and must be listed explicitly: the root project applies no
// "java" plugin, so it has no source sets for the scanner to infer
// anything from, and without this the root module contributes nothing.
//
// NOTE: ":app" is deliberately absent. It contributes its own
// "sonar.sources" and "sonar.tests" from its source sets as a separate
// module; naming app paths here as well would index them twice.
//
// NOTE: every entry must exist on disk or the scanner fails the run.
val rootSonarSources =
    listOf(
        ".github/workflows",
        "build.gradle.kts",
        "settings.gradle.kts",
        "Dockerfile",
        "docker-compose.yml",
    )

sonar {
    properties {
        // SONAR_TOKEN must be defined as an environment variable
        property("sonar.projectKey", sonarKey)
        property("sonar.projectName", sonarName)
        property("sonar.organization", sonarOrg)
        property("sonar.host.url", sonarUrl)
        property("sonar.sources", rootSonarSources.joinToString(","))
    }
}

// Analysis runs after ":app:check", which triggers ":app:test"; because
// "test" is finalizedBy jacocoTestReport, the XML coverage report exists
// by the time the scanner runs, so an explicit dependency on
// jacocoTestReport is not needed:
// tasks.sonar { dependsOn(":app:jacocoTestReport") }
//
// NOTE: the dependency is spelled ":app:check" rather than "check". The
// root project has no "check" task of its own -- it applies neither
// "java" nor "base" -- so the unqualified name would not resolve.
tasks.sonar { dependsOn(":app:check") }
