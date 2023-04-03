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

import net.kautler.dao.action.GitHubAction
import net.kautler.util.npm
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.tasks.IncrementalSyncTask
import org.yaml.snakeyaml.Yaml

plugins {
    kotlin("multiplatform")
}

val libs = the<LibrariesForLibs>()

kotlin {
    js {
        useCommonJs()
        binaries.executable()
        nodejs()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(dependencies.platform(libs.kotlin.wrappers.bom))
                implementation(libs.kotlin.wrapper.actions.toolkit)
                implementation(libs.kotlin.wrapper.js)
                implementation(libs.kotlin.wrapper.node)
                implementation(npm(libs.semver))
                implementation(npm(libs.nullWritable))
            }
        }
    }
}

// work-around for https://youtrack.jetbrains.com/issue/KT-56305
tasks.withType<IncrementalSyncTask>().configureEach {
    doFirst {
        outputs.files.forEach { it.deleteRecursively() }
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

tasks.assemble {
    dependsOn(project(":ncc-packer").tasks.named("jsNodeProductionRun"))
}
