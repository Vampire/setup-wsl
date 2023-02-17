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

import net.kautler.util.npm
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec

plugins {
    kotlin("js")
}

kotlin {
    js(IR) {
        useCommonJs()
        binaries.executable()
        nodejs()
    }
}

// work-around for https://youtrack.jetbrains.com/issue/KT-56305
tasks.withType<Copy>().configureEach {
    if (name.endsWith("ExecutableCompileSync")) {
        doFirst {
            outputs.files.forEach { it.deleteRecursively() }
        }
    }
}

// work-around for https://youtrack.jetbrains.com/issue/KT-56305
tasks.withType<NodeJsExec>().configureEach {
    abstract class ArgumentProvider @Inject constructor(rootProject: Project) : CommandLineArgumentProvider {
        @get:InputFile
        @get:SkipWhenEmpty
        abstract val input: RegularFileProperty

        @get:OutputDirectory
        abstract val output: DirectoryProperty

        init {
            input.fileProvider(
                rootProject
                    .tasks
                    .named<Copy>("productionExecutableCompileSync")
                    .map {
                        it
                            .outputs
                            .files
                            .asFileTree
                            .matching { include("${rootProject.name}.js") }
                            .singleFile
                    }
            )

            output.set(rootProject.layout.buildDirectory.dir("distributions"))
        }

        override fun asArguments(): MutableIterable<String> =
            mutableListOf(
                input.get().asFile.absolutePath,
                output.get().asFile.absolutePath
            )
    }
    argumentProviders.add(objects.newInstance<ArgumentProvider>(rootProject))
}

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.kotlin.wrappers.bom))
    implementation(libs.kotlin.wrapper.js)
    implementation(libs.kotlin.wrapper.node)
    implementation(npm(libs.build.vercel.ncc))
}
