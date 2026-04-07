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

import org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS
import org.gradle.api.initialization.resolve.RulesMode.FAIL_ON_PROJECT_RULES

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.4.0"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.5.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    repositoriesMode = FAIL_ON_PROJECT_REPOS
    rulesMode = FAIL_ON_PROJECT_RULES

    versionCatalogs {
        create("libs") {
            from(files("../libs.versions.toml"))
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

rootProject.name = "conditional-refresh-versions"
rootProject.buildFileName = "conditional-refresh-versions.gradle.kts"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
