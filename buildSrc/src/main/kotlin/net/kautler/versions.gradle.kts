/*
 * Copyright 2020 Björn Kautler
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration.Companion.Stable
import net.kautler.dao.ResultSerializer
import net.kautler.util.matches
import net.kautler.util.updateCounts

plugins {
    id("com.github.ben-manes.versions")
}

val majorVersion by extra("$version".substringBefore('.'))

val buildSrcDependencyUpdates by tasks.registering(GradleBuild::class) {
    dir = file("buildSrc")
    buildName = "buildSrc-for-dependencyUpdates"
    tasks = listOf("dependencyUpdates")
}

tasks.dependencyUpdates {
    dependsOn(buildSrcDependencyUpdates)

    gradleReleaseChannel = CURRENT.id
    checkConstraints = true

    rejectVersionIf {
        val preliminaryReleaseRegex = Regex("""(?i)[.-](?:${
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
        })[.\d-]*""")
        preliminaryReleaseRegex.containsMatchIn(candidate.version)
                && !preliminaryReleaseRegex.containsMatchIn(currentVersion)
    }

    outputFormatter = closureOf<Result> {
        val buildSrcResultFile = file("buildSrc/build/dependencyUpdates/report.json")
        if (buildSrcResultFile.isFile) {
            val buildSrcResult = Json(Stable).parse(ResultSerializer, buildSrcResultFile.readText())
            current.dependencies.addAll(buildSrcResult.current.dependencies)
            outdated.dependencies.addAll(buildSrcResult.outdated.dependencies)
            exceeded.dependencies.addAll(buildSrcResult.exceeded.dependencies)
            unresolved.dependencies.addAll(buildSrcResult.unresolved.dependencies)
        }

        val ignored = outdated.dependencies.filter {
            // This plugin should always be used without version as it is tightly
            // tied to the Gradle version that is building the precompiled script plugins
            it.matches("org.gradle.kotlin.kotlin-dsl", "org.gradle.kotlin.kotlin-dsl.gradle.plugin")
                    // Not until Gradle is on Kotlin 1.4
                    || it.matches("com.charleskorn.kaml", "kaml")
                    || it.matches("org.jetbrains.kotlin", "kotlin-reflect")
                    || it.matches("org.jetbrains.kotlin", "kotlin-sam-with-receiver")
                    || it.matches("org.jetbrains.kotlin", "kotlin-scripting-compiler-embeddable")
                    || it.matches("org.jetbrains.kotlin", "kotlin-scripting-jvm-host")
                    || it.matches("org.jetbrains.kotlin", "kotlin-serialization")
                    || it.matches("org.jetbrains.kotlin", "kotlin-serialization-unshaded")
                    || it.matches("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
                    || it.matches("org.jetbrains.kotlin.plugin.serialization", "org.jetbrains.kotlin.plugin.serialization.gradle.plugin")
                    || it.matches("org.jetbrains.kotlinx", "kotlinx-serialization-runtime")
        }

        outdated.dependencies.removeAll(ignored)
        updateCounts()

        PlainTextReporter(project, revisionLevel(), gradleReleaseChannelLevel())
                .write(System.out, this)

        if (ignored.isNotEmpty()) {
            println("\nThe following dependencies have later ${revisionLevel()} versions but were ignored:")
            ignored.forEach {
                println(" - ${it.group}:${it.name} [${it.version} -> ${it.available.getProperty(revisionLevel())}]")
                it.projectUrl?.let { println("     $it") }
            }
        }

        if (gradle.current.isFailure || (unresolved.count != 0)) {
            error("Unresolved libraries found")
        }

        if (gradle.current.isUpdateAvailable || (outdated.count != 0)) {
            error("Outdated libraries found")
        }
    }
}
