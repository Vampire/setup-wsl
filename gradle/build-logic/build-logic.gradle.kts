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
    alias(libs.plugins.convention.dependency.updates.report.aggregatee)
    id(libs.plugins.dependency.analysis.get().pluginId)
}

dependencies {
    compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    compileOnly(files(kotlinWrappers.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(plugin(libs.plugins.versions))
    // part of work-around for https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1672
    //implementation(plugin(libs.plugins.dependency.analysis))
    implementation(plugin(libs.plugins.release))
    implementation(plugin(libs.plugins.grgit))
    implementation(plugin(libs.plugins.github))
    implementation(plugin(libs.plugins.kotlin.multiplatform))
    implementation(":dependency-updates-report-aggregation")
    implementation(platform(libs.build.kotlinx.serialization.bom))
    implementation(libs.build.kotlinx.serialization.json)
    implementation(libs.build.github.api)
    implementation(libs.build.snakeyaml)
    compileOnly(libs.build.inject)
    compileOnly(embeddedKotlin("compiler-embeddable"))
    // just to get update notifications by versions plugin
    compileOnly(plugin(libs.plugins.refresh.versions))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencyAnalysis {
    structure {
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
        bundle("org.jetbrains.kotlin.multiplatform.gradle.plugin") {
            includeDependency("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin")
            includeDependency("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
            includeDependency("org.jetbrains.kotlin:kotlin-gradle-plugin")
        }
    }
    issues {
        all {
            onAny {
                severity("fail")
            }
            onDuplicateClassWarnings {
                // work-around for https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1629
                severity("fail")
                // present in kotlin-compiler-embeddable and kotlin-gradle-plugin
                exclude("org/jetbrains/kotlin/cli/common/messages/MessageCollector")
                exclude("org/jetbrains/kotlin/cli/common/messages/MessageCollector\$Companion")
                exclude("org/jetbrains/kotlin/com/intellij/openapi/Disposable")
                exclude("org/jetbrains/kotlin/com/intellij/openapi/util/Disposer")
            }
        }
    }
    reporting {
        printBuildHealth(true)
    }
}

tasks.buildHealth {
    dependsOn(gradle.includedBuilds.map { it.task(":buildHealth") })
}

tasks.check {
    dependsOn(tasks.buildHealth)
}

fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}
