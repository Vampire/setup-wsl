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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    alias(libs.plugins.convention.dependency.updates.report.aggregation)
    alias(libs.plugins.dependency.analysis)
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(plugin(libs.plugins.versions))
    implementation(plugin(libs.plugins.dependency.analysis))
    implementation(plugin(libs.plugins.release))
    implementation(plugin(libs.plugins.grgit))
    implementation(plugin(libs.plugins.github))
    implementation(plugin(libs.plugins.kotlin.js))
    implementation(":dependency-updates-report-aggregation")
    implementation(libs.build.inject)
    implementation(libs.build.github.api)
    implementation(libs.build.snakeyaml)
    implementation(embeddedKotlin("compiler-embeddable"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencyAnalysis {
    dependencies {
        bundle("com.autonomousapps.dependency-analysis.gradle.plugin") {
            includeDependency("com.autonomousapps.dependency-analysis:com.autonomousapps.dependency-analysis.gradle.plugin")
            includeDependency("com.autonomousapps:dependency-analysis-gradle-plugin")
        }
        bundle("com.github.ben-manes.versions.gradle.plugin") {
            includeDependency("com.github.ben-manes.versions:com.github.ben-manes.versions.gradle.plugin")
            includeDependency("com.github.ben-manes:gradle-versions-plugin")
        }
        bundle("net.researchgate.release.gradle.plugin") {
            includeDependency("net.researchgate.release:net.researchgate.release.gradle.plugin")
            includeDependency("net.researchgate:gradle-release")
        }
        bundle("net.wooga.github.gradle.plugin") {
            includeDependency("net.wooga.github:net.wooga.github.gradle.plugin")
            includeDependency("net.wooga.gradle:github")
        }
        bundle("org.ajoberstar.grgit.service.gradle.plugin") {
            includeDependency("org.ajoberstar.grgit.service:org.ajoberstar.grgit.service.gradle.plugin")
            includeDependency("org.ajoberstar.grgit:grgit-gradle")
            includeDependency("org.ajoberstar.grgit:grgit-core")
        }
        bundle("org.jetbrains.kotlin.js.gradle.plugin") {
            includeDependency("org.jetbrains.kotlin.js:org.jetbrains.kotlin.js.gradle.plugin")
            includeDependency("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
            includeDependency("org.jetbrains.kotlin:kotlin-gradle-plugin")
        }
    }
    issues {
        all {
            onAny {
                // the "implementation(files(...)) is reported as false-positive unused
                // and cannot be suppressed, so we cannot let the task fail currently
                //severity("fail")
            }
            onUsedTransitiveDependencies {
                // false positive
                exclude(":dependency-updates-report-aggregation")
            }
        }
    }
}

tasks.configureEach {
    if (name == "buildHealth") {
        dependsOn(gradle.includedBuilds.map { it.task(":buildHealth") })
    }
}

fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}
