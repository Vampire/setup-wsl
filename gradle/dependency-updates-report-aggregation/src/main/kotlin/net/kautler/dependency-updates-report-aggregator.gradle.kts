/*
 * Copyright 2026 Björn Kautler
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
import com.github.benmanes.gradle.versions.reporter.result.Result
import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel.CURRENT
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.kautler.dao.result.ResultSerializer
import net.kautler.util.IgnoredDependency
import net.kautler.util.PreliminaryReleaseFilter
import net.kautler.util.ProblemsProvider
import net.kautler.util.matches
import net.kautler.util.withUpdatedCounts
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.VERIFICATION
import org.gradle.api.attributes.VerificationType.VERIFICATION_TYPE_ATTRIBUTE

plugins {
    id("com.github.ben-manes.versions")
}

val dependencyUpdatesResultsDependencies = configurations.dependencyScope("dependencyUpdatesResultsDependencies")
val dependencyUpdatesResults = configurations.resolvable("dependencyUpdatesResults") {
    extendsFrom(dependencyUpdatesResultsDependencies.get())
    attributes {
        attribute(CATEGORY_ATTRIBUTE, objects.named(VERIFICATION))
        attribute(VERIFICATION_TYPE_ATTRIBUTE, objects.named("dependency-updates-result"))
    }
}

val includedBuildNames = gradle
    .includedBuilds
    .map { it.name }

dependencies {
    includedBuildNames
        .filterNot { it == "conditional-refresh-versions" }
        .forEach { dependencyUpdatesResultsDependencies(":$it") }
}

tasks.dependencyUpdates {
    inputs
        .files(dependencyUpdatesResults)
        .withPropertyName("dependencyUpdatesResults")

    gradleReleaseChannel = CURRENT.id
    checkConstraints = true
    rejectVersionIf(PreliminaryReleaseFilter)

    val ignoredDependencies = objects.domainObjectSet(IgnoredDependency::class)
    extensions.add<DomainObjectCollection<IgnoredDependency>>("ignoredDependencies", ignoredDependencies)

    // copies for configuration cache compatibility
    val dependencyUpdatesResults: FileCollection = dependencyUpdatesResults.get()
    val includedBuildNames = includedBuildNames

    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    outputFormatter = closureOf<Result> {
        dependencyUpdatesResults.files.forEach { dependencyUpdatesResultFile ->
            val dependencyUpdatesResult = dependencyUpdatesResultFile.inputStream().use {
                @OptIn(ExperimentalSerializationApi::class)
                Json.Default.decodeFromStream(ResultSerializer, it)
            }
            current.dependencies.addAll(dependencyUpdatesResult.current.dependencies)
            outdated.dependencies.addAll(dependencyUpdatesResult.outdated.dependencies)
            exceeded.dependencies.addAll(dependencyUpdatesResult.exceeded.dependencies)
            undeclared.dependencies.addAll(dependencyUpdatesResult.undeclared.dependencies)
            unresolved.dependencies.addAll(dependencyUpdatesResult.unresolved.dependencies)
        }

        val ignored = outdated
            .dependencies
            .filter { ignoredDependencies.any(it::matches) }

        outdated.dependencies.removeAll(ignored.toSet())

        unresolved.dependencies.removeAll {
            (it.group == "null") &&
                ((it.name in includedBuildNames) || (it.name == "dependency-updates-report-aggregation")) &&
                (it.version == "+")
        }

        val result = withUpdatedCounts

        PlainTextReporter(project, revision, gradleReleaseChannel)
            .write(System.out, result)

        if (ignored.isNotEmpty()) {
            println("\nThe following dependencies have later $revision versions but were ignored:")
            ignored.forEach {
                println(" - ${it.group}:${it.name} [${it.version} -> ${it.available[revision]}]")
                it.projectUrl?.let { println("     $it") }
            }
        }

        val problems = buildList {
            val dependenciesGroup = ProblemGroup.create("dependencies", "Dependencies")

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
