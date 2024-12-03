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
import net.kautler.conditionalRefreshVersions
import org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS

pluginManagement {
    includeBuild("../dependency-updates-report-aggregation")
    includeBuild("../conditional-refresh-versions")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("net.kautler.conditional-refresh-versions")
}

conditionalRefreshVersions {
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

// work-around for https://github.com/jmfayard/refreshVersions/issues/596
gradle.rootProject {
    tasks.configureEach {
        if (name == "refreshVersions") {
            doFirst {
                copy {
                    from(gradle.parent!!.rootProject.file("gradle/libs.versions.toml"))
                    into("gradle")
                }
            }
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
                copy {
                    from("gradle/libs.versions.toml")
                    into(gradle.parent!!.rootProject.file("gradle"))
                }
                delete("gradle")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    repositoriesMode.set(FAIL_ON_PROJECT_REPOS)

    versionCatalogs {
        val libs by registering {
            from(files("../libs.versions.toml"))
        }

        val kotlinWrappers by registering {
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:0.0.1-pre.819")
        }
    }
}

rootProject.name = "build-logic"
rootProject.buildFileName = "build-logic.gradle.kts"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
