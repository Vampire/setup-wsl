/*
 * Copyright 2020-2023 Björn Kautler
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
 * limitations under the License.
 */

import de.fayard.refreshVersions.core.FeatureFlag.GRADLE_UPDATES
import org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_SETTINGS

pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.51.0"
    id("com.gradle.enterprise") version "3.12.3"
}

refreshVersions {
    featureFlags {
        disable(GRADLE_UPDATES)
    }
    rejectVersionIf {
        candidate.stabilityLevel.isLessStableThan(current.stabilityLevel)
    }
    // work-around for https://github.com/jmfayard/refreshVersions/issues/662
    file("build/tmp/refreshVersions").mkdirs()
    // work-around for https://github.com/jmfayard/refreshVersions/issues/640
    versionsPropertiesFile = file("build/tmp/refreshVersions/versions.properties")
}

gradle.rootProject {
    tasks.configureEach {
        if (name == "refreshVersions") {
            doLast {
                // work-around for https://github.com/jmfayard/refreshVersions/issues/661
                // and https://github.com/jmfayard/refreshVersions/issues/663
                file("gradle/libs.versions.toml").apply {
                    readText()
                        .replace("⬆ =", " ⬆ =")
                        .replace("]\n\n", "]\n")
                        .replace("""(?s)^(.*)(\n\Q[plugins]\E[^\[]*)(\n.*)$""".toRegex(), "$1$3$2")
                        .also { writeText(it) }
                }
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        ivy("https://nodejs.org/dist/") {
            name = "Node.js Distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download/") {
            name = "Yarn Distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.yarnpkg", "yarn")
            }
        }
        mavenCentral()
    }
    // work-around for https://youtrack.jetbrains.com/issue/KT-56300
    //repositoriesMode.set(FAIL_ON_PROJECT_REPOS)
    repositoriesMode.set(PREFER_SETTINGS)
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

includeBuild("gradle/build-logic")
include("ncc-packer")
project(":ncc-packer").buildFileName = "ncc-packer.gradle.kts"

rootProject.name = "setup-wsl"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
