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

import com.autonomousapps.DependencyAnalysisExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// part of work-around for https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/issues/719
buildscript {
    if (!JavaVersion.current().isJava11Compatible) {
        dependencies {
            components {
                listOf(
                    "com.autonomousapps:dependency-analysis-gradle-plugin",
                    "com.autonomousapps:graph-support"
                ).forEach {
                    withModule(it) {
                        allVariants {
                            attributes {
                                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                            }
                        }
                    }
                }
            }
        }
    }
}

plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.versions)
    // part of work-around for https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/issues/719
    alias(libs.plugins.dependency.analysis) apply JavaVersion.current().isJava11Compatible
}

dependencies {
    implementation(plugin(libs.plugins.versions))
    implementation(platform(libs.build.kotlinx.serialization.bom))
    implementation(libs.build.kotlinx.serialization.core)
    implementation(libs.build.kotlinx.serialization.json)
}

// part of work-around for https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/issues/719
if (JavaVersion.current().isJava11Compatible) {
    configure<DependencyAnalysisExtension> {
        dependencies {
            bundle("com.github.ben-manes.versions.gradle.plugin") {
                includeDependency("com.github.ben-manes.versions:com.github.ben-manes.versions.gradle.plugin")
                includeDependency("com.github.ben-manes:gradle-versions-plugin")
            }
        }
        issues {
            all {
                onAny {
                    severity("fail")
                }
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        allWarningsAsErrors = true
    }
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.dependencyUpdates {
    checkForGradleUpdate = false
    checkConstraints = true
}

fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}
