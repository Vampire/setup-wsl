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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    alias(libs.plugins.versions)
    id(libs.plugins.dependency.analysis.get().pluginId)
}

dependencies {
    implementation(plugin(libs.plugins.refresh.versions))
}

dependencyAnalysis {
    structure {
        bundle("de.fayard.refreshVersions.gradle.plugin") {
            includeDependency("de.fayard.refreshVersions:de.fayard.refreshVersions.gradle.plugin")
            includeDependency("de.fayard.refreshVersions:refreshVersions")
            includeDependency("de.fayard.refreshVersions:refreshVersions-core")
        }
    }
    issues {
        all {
            onAny {
                severity("fail")
            }
            // work-around for https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1629
            onDuplicateClassWarnings {
                severity("fail")
            }
        }
    }
    reporting {
        printBuildHealth(true)
    }
}

tasks.check {
    dependsOn(tasks.buildHealth)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

tasks.dependencyUpdates {
    checkForGradleUpdate = false
    checkConstraints = true
}

fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}
