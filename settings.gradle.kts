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
 * This is a blueprint Gradle settings.gradle.kts file used by Rubens Gomes
 * during the creation of a new Gradle Java development project.
 *
 * Formatting note: this header is a plain block comment, NOT a KDoc comment,
 * and the licence text is merged into it rather than sitting in a comment of
 * its own. Both are load-bearing. ktlint's "standard:kdoc" rule rejects KDoc
 * at the top level of a Gradle script ("A KDoc is not allowed inside
 * 'block'"), and "standard:no-consecutive-comments" rejects a block comment
 * preceded by another block comment. Either one fails
 * ":spotlessKotlinGradleApply".
 *
 * @author [Rubens Gomes](https://rubensgomes.com)
 */

// The project name should match the root folder
rootProject.name = "spring-blueprint"
// The project type should match "app" or "lib" depending on project nature
include("app")

// ------------------- Plugin Management -------------------
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    // NOTE: no plugin versions are pinned here. Every third-party plugin is
    // applied in "app/build.gradle.kts" via alias(libs.plugins.*), so the
    // shared "com.rubensgomes:gradle-catalog" is the single source of truth
    // for versions. A version declared on the plugin request always wins over
    // a pluginManagement default, so pinning here would have no effect.
}

// ------------------- Global Plugins -------------------
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// ------------------- Dependency Resolution -------------------
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {

    // Helper function to configure GitHub Maven repos with credentials
    fun org.gradle.api.artifacts.dsl.RepositoryHandler.githubRepo(url: String?) {
        if (url.isNullOrBlank()) return

        val githubUser = System.getenv("GITHUB_USER")
        val githubToken = System.getenv("GITHUB_TOKEN")

        // NOTE: deliberately a warning rather than an error. Once the catalog
        // and any com.rubensgomes artifacts are in the Gradle module cache the
        // build resolves them without contacting GitHub, so an unauthenticated
        // build is valid. It is the first build on a cold cache -- a fresh
        // clone of this blueprint -- that fails, and it fails with a 401 that
        // never mentions which variables are missing.
        if (githubUser.isNullOrBlank() || githubToken.isNullOrBlank()) {
            org.gradle.api.logging.Logging.getLogger("settings").warn(
                "GITHUB_USER and/or GITHUB_TOKEN are not set. Artifacts not already " +
                    "in the Gradle cache cannot be downloaded from $url. Export both " +
                    "variables if dependency resolution fails with HTTP 401.",
            )
        }

        maven {
            setUrl(url)
            credentials {
                username = githubUser
                password = githubToken
            }
        }
    }

    // Fetch GitHub repo URLs directly from gradle.properties
    val mavenRepoPackages =
        settings.extra.properties["mavenRepoPackages"] as? String
    repositories {
        mavenCentral()
        githubRepo(mavenRepoPackages)
    }

    versionCatalogs {
        create("libs") {
            from("com.rubensgomes:gradle-catalog:0.2.9")
        }
    }
}
