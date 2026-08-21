/*
 * Copyright 2025 Rubens Gomes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the__LICENSE] [1].
 */

/**
 * This is a blueprint Gradle settings.gradle.kts file used by Rubens Gomes
 * during the creation of a new Gradle Java developement project.
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

    // Fetch releasePluginVersion directly from gradle.properties
    val releasePluginVersion = 
      settings.extra.properties["releasePluginVersion"] as? String
        ?: throw GradleException("Property 'releasePluginVersion' not found in gradle.properties")

    plugins {
        id("net.researchgate.release") version releasePluginVersion
    }
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
        if (!url.isNullOrBlank()) {
            maven {
                setUrl(url)
                credentials {
                    username = System.getenv("GITHUB_USER")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    // Fetch GitHub repo URLs directly from gradle.properties
    val mavenRepoPackages = 
        settings.extra.properties["mavenRepoPackages"] as? String
    repositories {
        mavenCentral()
        google()
        githubRepo(mavenRepoPackages)
    }

    versionCatalogs {
        create("libs") {
            from("com.rubensgomes:gradle-catalog:0.2.0")
        }
    }
}
