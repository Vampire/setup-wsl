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

import net.kautler.util.NullOutputStream
import net.kautler.util.add
import net.kautler.util.ignoredDependencies
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

plugins {
    id("net.kautler.dependency-updates-report-aggregation")
    id("com.autonomousapps.dependency-analysis")
}

val majorVersion by extra("$version".substringBefore('.'))

val validateGradleWrapperJar by tasks.registering {
    onlyIf {
        !gradle.startParameter.isOffline
    }

    doLast {
        val expectedDigest = URL("https://services.gradle.org/distributions/gradle-${gradle.gradleVersion}-wrapper.jar.sha256").readText()

        val sha256 = MessageDigest.getInstance("SHA-256")
        layout
            .projectDirectory
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

        check(expectedDigest == actualDigest) {
            "The wrapper JAR does not match the configured Gradle version, please update the wrapper"
        }
    }
}

tasks.dependencyUpdates {
    dependsOn(validateGradleWrapperJar)

    ignoredDependencies {
        // This plugin should always be used without version as it is tightly
        // tied to the Gradle version that is building the precompiled script plugins
        add(group = "org.gradle.kotlin.kotlin-dsl", name = "org.gradle.kotlin.kotlin-dsl.gradle.plugin")
        // These dependencies are used in the build logic so should match the
        // embedded Kotlin version and not be upgraded independently
        add(group = "org.jetbrains.kotlin", name = "kotlin-compiler-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-klib-commonizer-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-reflect")
        add(group = "org.jetbrains.kotlin", name = "kotlin-sam-with-receiver")
        add(group = "org.jetbrains.kotlin", name = "kotlin-scripting-compiler-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8")
    }
}

dependencyAnalysis {
    issues {
        all {
            onAny {
                severity("fail")
            }
        }
    }
}

tasks.configureEach {
    if (name == "buildHealth") {
        dependsOn(gradle.includedBuilds.map { it.task(":buildHealth") })
    }
}
