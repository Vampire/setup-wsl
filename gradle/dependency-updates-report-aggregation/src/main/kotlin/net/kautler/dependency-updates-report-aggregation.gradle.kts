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

package net.kautler

import com.github.benmanes.gradle.versions.reporter.PlainTextReporter
import com.github.benmanes.gradle.versions.reporter.result.Result
import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel.CURRENT
import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentSelectionWithCurrent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.kautler.dao.result.ResultSerializer
import net.kautler.util.IgnoredDependency
import net.kautler.util.matches
import net.kautler.util.withUpdatedCounts
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.VERIFICATION
import org.gradle.api.attributes.VerificationType.VERIFICATION_TYPE_ATTRIBUTE

plugins {
    id("com.github.ben-manes.versions")
}

val isPrelimiaryRelease: ComponentSelectionWithCurrent.() -> Boolean = {
    val preliminaryReleaseRegex = Regex(
        """(?i)[.-](?:${
            listOf(
                "alpha",
                "beta",
                "dev",
                "rc",
                "cr",
                "m",
                "preview",
                "test",
                "pr",
                "pre",
                "b",
                "ea"
            ).joinToString("|")
        })[.\d-]*"""
    )
    preliminaryReleaseRegex.containsMatchIn(candidate.version)
            && !preliminaryReleaseRegex.containsMatchIn(currentVersion)
}

val json = Json { prettyPrint = true }

if (gradle.parent == null && parent == null) {
    // we are in the root project of the root build
    val dependencyUpdatesResults by configurations.creating {
        isCanBeConsumed = false
        isCanBeResolved = true
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
            .forEach { dependencyUpdatesResults(":$it") }
    }

    tasks.dependencyUpdates {
        inputs
            .files(dependencyUpdatesResults)
            .withPropertyName("dependencyUpdatesResults")

        gradleReleaseChannel = CURRENT.id
        checkConstraints = true
        rejectVersionIf(isPrelimiaryRelease)

        val ignoredDependencies = objects.domainObjectSet(IgnoredDependency::class)
        extensions.add<DomainObjectCollection<IgnoredDependency>>("ignoredDependencies", ignoredDependencies)

        outputFormatter = closureOf<Result> {
            dependencyUpdatesResults.files.forEach { dependencyUpdatesResultFile ->
                val dependencyUpdatesResult = dependencyUpdatesResultFile.inputStream().use {
                    @OptIn(ExperimentalSerializationApi::class)
                    json.decodeFromStream(ResultSerializer, it)
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
                (it.group == "null")
                        && ((it.name in includedBuildNames) || (it.name == "dependency-updates-report-aggregation"))
                        && (it.version == "+")
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

            if (gradle.current.isFailure || (result.unresolved.count != 0)) {
                error("Unresolved libraries found")
            }

            if (gradle.current.isUpdateAvailable || (result.outdated.count != 0)) {
                error("Outdated libraries found")
            }
        }
    }
} else {
    val dependencyUpdatesResult by configurations.creating {
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes {
            attribute(CATEGORY_ATTRIBUTE, objects.named(VERIFICATION))
            attribute(VERIFICATION_TYPE_ATTRIBUTE, objects.named("dependency-updates-result"))
        }
    }

    tasks.dependencyUpdates {
        val reportFile = file("build/dependencyUpdates/dependencyUpdatesReport.json")
        outputs.file(reportFile).withPropertyName("reportFile")

        checkForGradleUpdate = false
        checkConstraints = true
        rejectVersionIf(isPrelimiaryRelease)

        outputFormatter = closureOf<Result> {
            reportFile
                .apply { parentFile.mkdirs() }
                .outputStream()
                .use { reportFileStream ->
                    @OptIn(ExperimentalSerializationApi::class)
                    json.encodeToStream(ResultSerializer, this, reportFileStream)
                }
        }
    }

    artifacts {
        add(dependencyUpdatesResult.name, tasks.dependencyUpdates)
    }
}
