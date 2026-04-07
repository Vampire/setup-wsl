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

import net.kautler.util.NullOutputStream
import net.kautler.util.PreliminaryReleaseFilter
import net.kautler.util.ProblemsProvider
import net.kautler.util.add
import net.kautler.util.ignoredDependencies
import java.security.DigestInputStream
import java.security.MessageDigest

plugins {
    `lifecycle-base`
    id("net.kautler.dependency-updates-report-aggregator")
    id("com.autonomousapps.dependency-analysis")
}

val majorVersion by extra("$version".substringBefore('.'))

val validateGradleWrapperJar by tasks.registering {
    val offlineBuild = gradle.startParameter.isOffline
    onlyIf { !offlineBuild }

    val resources = resources
    val gradleVersion = gradle.gradleVersion
    val projectDirectory = layout.projectDirectory
    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    doLast {
        val expectedDigest = resources.text.fromUri("https://services.gradle.org/distributions/gradle-$gradleVersion-wrapper.jar.sha256").asString()

        val sha256 = MessageDigest.getInstance("SHA-256")
        projectDirectory
            .dir("gradle")
            .dir("wrapper")
            .file("gradle-wrapper.jar")
            .asFile
            .inputStream()
            .let { DigestInputStream(it, sha256) }
            .use { it.copyTo(NullOutputStream()) }
        val actualDigest = sha256.digest().let {
            "%02x".repeat(it.size).format(*it.toTypedArray())
        }

        if (expectedDigest != actualDigest) {
            throw problemReporter.throwing(
                IllegalStateException(),
                ProblemId.create(
                    "the-wrapper-jar-does-not-match-the-configured-gradle-version",
                    "The wrapper JAR does not match the configured Gradle version",
                    ProblemGroup.create("build-authoring", "Build Authoring")
                )
            ) {
                solution("Update the wrapper to the version of Gradle")
                severity(Severity.ERROR)
            }
        }
    }
}

tasks.dependencyUpdates {
    dependsOn(validateGradleWrapperJar)

    rejectVersionIf {
        if (PreliminaryReleaseFilter.reject(this)) {
            reject("preliminary release")
        }

        // branches above already rejected with appropriate reason
        return@rejectVersionIf false
    }

    ignoredDependencies {
        // This plugin should always be used without version as it is tightly
        // tied to the Gradle version that is building the precompiled script plugins
        add(group = "org.gradle.kotlin.kotlin-dsl", name = "org.gradle.kotlin.kotlin-dsl.gradle.plugin")
        // These dependencies are used in the build logic so should match the
        // embedded Kotlin version and not be upgraded independently
        add(group = "org.jetbrains.kotlin", name = "kotlin-assignment-compiler-plugin-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-build-tools-impl")
        add(group = "org.jetbrains.kotlin", name = "kotlin-compiler-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-reflect")
        add(group = "org.jetbrains.kotlin", name = "kotlin-sam-with-receiver-compiler-plugin-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-scripting-compiler-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-stdlib")
    }
}

dependencyAnalysis {
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

tasks.buildHealth {
    dependsOn(gradle.includedBuilds.map { it.task(":buildHealth") })
}

tasks.check {
    dependsOn(tasks.buildHealth)
}
