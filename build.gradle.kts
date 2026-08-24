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
