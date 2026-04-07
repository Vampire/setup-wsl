/*
 * Copyright 2020-2026 Björn Kautler
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
import org.gradle.api.initialization.resolve.RulesMode.FAIL_ON_PROJECT_RULES

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
    id("com.autonomousapps.build-health") version "3.1.0"
    embeddedKotlin("jvm") apply false
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.4.0"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.5.0"
}

conditionalRefreshVersions {
    featureFlags {
        disable(GRADLE_UPDATES)
    }
    rejectVersionIf {
        candidate.stabilityLevel.isLessStableThan(current.stabilityLevel)
    }
    // work-around for https://github.com/Splitties/refreshVersions/issues/662
    layout.rootDirectory.dir("build/tmp/refreshVersions").asFile.mkdirs()
    // work-around for https://github.com/Splitties/refreshVersions/issues/640
    versionsPropertiesFile = layout.rootDirectory.file("build/tmp/refreshVersions/versions.properties").asFile
}

// work-around for https://github.com/Splitties/refreshVersions/issues/596
gradle.rootProject {
    val copyVersionCatalog by tasks.registering {
        val fs = objects.newInstance<FileSystemOperationsProvider>().fs
        val versionCatalog = gradle.parent!!.rootProject.file("gradle/libs.versions.toml")
        doLast {
            fs.copy {
                from(versionCatalog)
                into("gradle")
            }
        }
    }
    tasks.named { it == "refreshVersions" }.configureEach {
        dependsOn(copyVersionCatalog)
        val layout = layout
        val fs = objects.newInstance<FileSystemOperationsProvider>().fs
        val gradleDirectory = gradle.parent!!.rootProject.file("gradle")
        doLast {
            // work-around for https://github.com/Splitties/refreshVersions/issues/661
            // and https://github.com/Splitties/refreshVersions/issues/663
            layout.projectDirectory.file("gradle/libs.versions.toml").asFile.apply {
                readText()
                    .replace("⬆ =", " ⬆ =")
                    .replace("⬆=", "⬆ =")
                    .replace("]\n\n", "]\n")
                    .replace("""(?s)^(.*)(\n\Q[plugins]\E[^\[]*)(\n.*)$""".toRegex(), "$1$3$2")
                    .also { writeText(it) }
            }
            fs.copy {
                from("gradle/libs.versions.toml")
                into(gradleDirectory)
            }
            fs.delete {
                delete("gradle/libs.versions.toml")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    repositoriesMode = FAIL_ON_PROJECT_REPOS
    rulesMode = FAIL_ON_PROJECT_RULES

    versionCatalogs {
        val libs by registering {
            from(files("../libs.versions.toml"))
        }

        val kotlinWrappers by registering {
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:2025.5.2")
        }
    }
}

develocity {
    buildScan {
        publishing {
            onlyIf {
                System.getenv("DEVELOCITY_INJECTION_ENABLED").toBoolean()
            }
        }
    }
}

rootProject.name = "build-logic"
rootProject.buildFileName = "build-logic.gradle.kts"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

interface FileSystemOperationsProvider {
    @get:Inject
    val fs: FileSystemOperations
}
