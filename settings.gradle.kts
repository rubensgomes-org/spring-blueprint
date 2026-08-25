/*
 * SPDX-License-Identifier: MIT
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

        // NOTE: deliberately a warning rather than an error.
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
