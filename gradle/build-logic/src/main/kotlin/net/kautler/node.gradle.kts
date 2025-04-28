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

import net.kautler.dao.action.GitHubAction
import net.kautler.util.npm
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.accessors.dm.LibrariesForKotlinWrappers
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.tasks.IncrementalSyncTask
import org.yaml.snakeyaml.Yaml

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
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                implementation(kotlinWrappers.actions.toolkit)
                implementation(npm(libs.actions.cache))
                implementation(kotlinWrappers.js)
                implementation(kotlinWrappers.node)
                implementation(kotlinWrappers.semver)
                implementation(kotlinWrappers.nullWritable)
            }
        }
    }
}

tasks.withType<IncrementalSyncTask>().configureEach {
    // work-around for https://youtrack.jetbrains.com/issue/KT-56305
    doFirst {
        outputs.files.forEach { it.deleteRecursively() }
    }

    // work-around for https://youtrack.jetbrains.com/issue/KTOR-6158
    doLast {
        outputs
            .files
            .asFileTree
            .filter { it.name == "setup-wsl.mjs" }
            .forEach {
                it
                    .readText()
                    .replace("eval('require')('abort-controller')", "globalThis.AbortController")
                    .replace("eval('require')('node-fetch')", "globalThis.fetch")
                    .replace("function readBodyNode(", "function _readBodyNode(")
                    .replace(" readBodyNode(", " readBodyBrowser(")
                    .apply(it::writeText)
            }
    }
}

val inputDefaultValues by lazy {
    file("action.yml")
        .inputStream()
        .use { Yaml().loadAs(it, GitHubAction::class.java) }
        .inputs
        ?.filterValues { it.default != null }
        ?.mapValues { it.value.default!! }
        ?.filterKeys { !System.getenv().containsKey("INPUT_${it.uppercase()}") }
}

// work-around for https://youtrack.jetbrains.com/issue/KT-56305
tasks.withType<NodeJsExec>().configureEach {
    val toolCacheDir = "$temporaryDir/tool-cache"

    // only execute safe actions that do not change the execution environment
    environment("INPUT_ONLY_SAFE_ACTIONS", true)

    environment("RUNNER_TEMP", "$temporaryDir/runner-temp")
    environment("RUNNER_TOOL_CACHE", toolCacheDir)

    inputDefaultValues?.forEach { (name, default) ->
        environment(
            "INPUT_${name.uppercase()}",
            if (name == "use-cache") "false" else default
        )
    }

    doFirst("Delete tool-cache") {
        file(toolCacheDir).deleteRecursively()
    }
}

configure<NodeJsRootExtension> {
    version = libs.versions.build.node.get()
}

val executable by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    isVisible = false
}

artifacts {
    val jsProductionExecutableCompileSync by tasks.existing(IncrementalSyncTask::class)
    add(
        executable.name,
        jsProductionExecutableCompileSync.map {
            it
                .destinationDirectory
                .get()
                .resolve("${project.name}.mjs")
        }
    )
}

// work-around for missing feature in dependencies block added in Gradle 8.3
//val setupWsl by configurations.registering {
val setupWslDistribution by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
    isVisible = false
}

val setupWslDistributionFiles by configurations.registering {
    isCanBeConsumed = false
    isCanBeResolved = true
    isVisible = false
    extendsFrom(setupWslDistribution)
}

dependencies {
    setupWslDistribution(project(path = ":ncc-packer", configuration = "setupWslDistribution"))
}

val syncDistribution by tasks.registering(Sync::class) {
    from(setupWslDistributionFiles)
    into(layout.buildDirectory.dir("distributions"))
    // work-around for https://github.com/actions/toolkit/issues/1925
    filesMatching("index.mjs") {
        filter {
            it.replace("stats = yield exports.stat", "stats = yield exports.lstat")
        }
    }
}

tasks.assemble {
    dependsOn(syncDistribution)
}
