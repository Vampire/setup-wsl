/*
 * Copyright 2020-2025 Björn Kautler
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

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.accessors.dm.LibrariesForKotlinWrappers
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalMainFunctionArgumentsDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.tasks.IncrementalSyncTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("multiplatform")
}

val libs = the<LibrariesForLibs>()
val kotlinWrappers = the<LibrariesForKotlinWrappers>()

kotlin {
    js {
        useEsModules()
        binaries.executable()
        nodejs()
        @OptIn(ExperimentalMainFunctionArgumentsDsl::class)
        passAsArgumentToMainFunction("process.argv.slice(2)")
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(kotlinWrappers.js)
                implementation(kotlinWrappers.node)
                implementation(kotlinWrappers.vercel.ncc)
            }
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

// work-around for https://youtrack.jetbrains.com/issue/KT-56305
tasks.withType<IncrementalSyncTask>().configureEach {
    doFirst {
        outputs.files.forEach { it.deleteRecursively() }
    }
}

configure<NodeJsEnvSpec> {
    downloadBaseUrl.set(provider { null })
}

// work-around for missing feature in dependencies block added in Gradle 8.3
//val setupWsl by configurations.registering {
val setupWslExecutable by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
    isVisible = false
}

val setupWslExecutableFile by configurations.registering {
    isCanBeConsumed = false
    isCanBeResolved = true
    isVisible = false
    extendsFrom(setupWslExecutable)
}

dependencies {
    setupWslExecutable(project(path = ":", configuration = "executable"))
}

// work-around for https://youtrack.jetbrains.com/issue/KT-56305
tasks.withType<NodeJsExec>().configureEach {
    val output by extra {
        layout.buildDirectory.dir("distributions/$name")
    }

    abstract class ArgumentProvider @Inject constructor(
        setupWslExecutableFile: Provider<File>,
        destinationDirectory: Provider<Directory>
    ) : CommandLineArgumentProvider {
        @get:InputFile
        abstract val input: RegularFileProperty

        @get:OutputDirectory
        abstract val destinationDirectory: DirectoryProperty

        init {
            input.fileProvider(setupWslExecutableFile)
            this.destinationDirectory.set(destinationDirectory)
        }

        override fun asArguments(): Iterable<String> =
            listOf(
                input.get().asFile.absolutePath,
                destinationDirectory.get().asFile.absolutePath
            )
    }

    argumentProviders.add(
        objects.newInstance<ArgumentProvider>(
            setupWslExecutableFile
                .flatMap { it.elements }
                .map { it.single().asFile },
            output
        )
    )

    doFirst {
        output.get().asFile.deleteRecursively()
    }
}

val setupWslDistribution by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    isVisible = false
}

artifacts {
    val jsNodeProductionRun by tasks.existing
    add(
        setupWslDistribution.name,
        jsNodeProductionRun.map {
            val output: Provider<Directory> by it.extra
            output.get()
        }
    )
}
