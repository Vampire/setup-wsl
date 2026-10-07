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

package net.kautler

import com.github.benmanes.gradle.versions.reporter.PlainTextReporter
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import net.kautler.util.IgnoredDependency
import net.kautler.util.NullOutputStream
import net.kautler.util.ProblemsProvider
import net.kautler.util.matches
import net.kautler.util.withUpdatedCounts
import java.security.DigestInputStream
import java.security.MessageDigest

plugins {
    `lifecycle-base`
    // part of work-around for https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1672
    //id("com.autonomousapps.dependency-analysis")
}

val majorVersion by extra("$version".substringBefore('.'))

val validateGradleWrapperJar by tasks.registering {
    val offlineBuild = gradle.startParameter.isOffline
    onlyIf { !offlineBuild }

    val resources = resources
    val projectDirectory = layout.projectDirectory
    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    doLast {
        val sha256 = MessageDigest.getInstance("SHA-256")
        projectDirectory
            .dir("gradle")
            .dir("wrapper")
            .file("gradle-wrapper.jar")
            .asFile
            .inputStream()
            .let { DigestInputStream(it, sha256) }
            .use { it.copyTo(NullOutputStream()) }
        val currentWrapperChecksum = sha256.digest().let {
            "%02x".repeat(it.size).format(*it.toTypedArray())
        }

        val gradleVersionOfWrapper = resources.text.fromUri("https://services.gradle.org/versions/all")
            .asString()
            .let(Json.Default::parseToJsonElement)
            .jsonArray
            .asSequence()
            .filterIsInstance<JsonObject>()
            .find {
                it["wrapperChecksumUrl"]
                    ?.jsonPrimitive
                    ?.content
                    ?.let(resources.text::fromUri)
                    ?.asString() == currentWrapperChecksum
            }
            ?.get("version")
            ?.jsonPrimitive
            ?.content
            ?.let(GradleVersion::version)

        if((gradleVersionOfWrapper == null) || (gradleVersionOfWrapper < GradleVersion.current())) {
            throw problemReporter.throwing(
                IllegalStateException(),
                ProblemId.create(
                    "the-wrapper-jar-is-not-from-the-configured-gradle-version-or-newer",
                    "The wrapper JAR is not from the configured Gradle version or newer",
                    ProblemGroup.create("build-authoring", "Build Authoring")
                )
            ) {
                solution("Update the wrapper to the version of Gradle or newer")
                severity(Severity.ERROR)
            }
        }
    }
}

val dependencyUpdatesAggregation by configurations.existing
dependencies {
    dependencyUpdatesAggregation(":build-logic")
    dependencyUpdatesAggregation(":conditional-refresh-versions")
}

val dependencyUpdates by tasks.existing(DependencyUpdatesTask::class) {
    dependsOn(validateGradleWrapperJar)

    checkConstraints = true
    checkBuildEnvironmentConstraints = true
    rejectPreReleases = true

    filterDeclaredConfigurations = Spec<String> { name ->
        val isKgpInternal = name in setOf(
            "kotlinCompilerClasspath",
            "kotlinBuildToolsApiClasspath",
            "kotlinAbiValidationCompatClasspath",
            "kotlinKlibCommonizerClasspath",
            "kotlinBouncyCastleConfiguration"
        ) || (
            name.startsWith("kotlinCompilerPluginClasspath") &&
                name != "kotlinCompilerPluginClasspath"
            )
        !isKgpInternal
    }

    val ignoredDependencies = listOf<IgnoredDependency>(
        // These dependencies are used in the build logic so should match the
        // embedded Kotlin version and not be upgraded independently
        IgnoredDependency(group = "org.jetbrains.kotlin", name = "kotlin-compiler-embeddable", oldVersion = embeddedKotlinVersion),
    )

    val projectPath = project.path
    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    outputFormatter {
        val ignored = outdated
            .dependencies
            .filter { ignoredDependencies.any(it::matches) }

        outdated.dependencies.removeAll(ignored.toSet())

        val result = withUpdatedCounts

        PlainTextReporter(projectPath, revision, gradleReleaseChannel, logger.isInfoEnabled)
            .write(System.out, result)

        if (ignored.isNotEmpty()) {
            println("\nThe following dependencies have later $revision versions but were ignored:")
            ignored.forEach {
                println(" - ${it.group}:${it.name} [${it.version} -> ${it.available[revision]}]")
                it.projectUrl?.let { println("     $it") }
            }
        }

        val problems = buildList {
            val dependenciesGroup = ProblemGroup.create("dependency-updates", "Dependency updates")

            if (gradle.current.isFailure) {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "gradle-version-could-not-be-checked",
                            "Gradle version could not be checked",
                            dependenciesGroup
                        )
                    ) {
                        solution("Retry later")
                        solution("Check the concrete error above")
                        severity(Severity.ERROR)
                    }
                )
            }

            if (result.unresolved.count != 0) {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "unresolved-libraries-found",
                            "Unresolved libraries found",
                            dependenciesGroup
                        )
                    ) {
                        solution("Retry later")
                        solution("Check the concrete error above")
                        solution("Find out why resolution failed")
                        severity(Severity.ERROR)
                    }
                )
            }

            if (gradle.current.isUpdateAvailable) {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "gradle-version-is-outdated",
                            "Gradle version is outdated",
                            dependenciesGroup
                        )
                    ) {
                        solution("Update Gradle")
                        severity(Severity.ERROR)
                    }
                )
            }

            if (result.outdated.count != 0) {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "outdated-libraries-found",
                            "Outdated libraries found",
                            dependenciesGroup
                        )
                    ) {
                        solution("Update the libraries")
                        solution("Add the outdated libraries to the list of ignored libraries")
                        severity(Severity.ERROR)
                    }
                )
            }
        }

        if (problems.isNotEmpty()) {
            throw problemReporter.throwing(IllegalStateException(), problems)
        }
    }
}

// part of work-around for https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1672
//dependencyAnalysis {
//    issues {
//        all {
//            onAny {
//                severity("fail")
//            }
//            // work-around for https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1629
//            onDuplicateClassWarnings {
//                severity("fail")
//            }
//        }
//    }
//    reporting {
//        printBuildHealth(true)
//    }
//}
//
//tasks.buildHealth {
val buildHealth by tasks.registering {
    dependsOn(gradle.includedBuilds.map { it.task(":buildHealth") })
}

tasks.check {
    // part of work-around for https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1672
    //dependsOn(tasks.buildHealth)
    dependsOn(buildHealth)
}
