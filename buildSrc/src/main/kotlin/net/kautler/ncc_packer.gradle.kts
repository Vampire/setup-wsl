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

import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

plugins {
    kotlin("js")
}

kotlin {
    js {
        useCommonJs()
        binaries.executable()
        nodejs {
            runTask {
                inputs
                        .files(rootProject.tasks.compileKotlinJs)
                        .skipWhenEmpty()
                        .withPropertyName("compiled JavaScript files")

                val output = rootProject.layout.buildDirectory.dir("distributions")
                outputs
                        .dir(output)
                        .withPropertyName("packaged JavaScript files")

                args(
                        rootProject.tasks.compileKotlinJs.get().outputFile.absolutePath,
                        output.get().asFile.absolutePath
                )
            }
        }
    }
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core".withVersion)
    implementation("org.jetbrains:kotlin-extensions".withVersion)
    implementation("org.jetbrains.kotlinx:kotlinx-nodejs".withVersion)
    implementation(npm("@vercel/ncc", generateExternals = false))
}

val TaskContainer.compileKotlinJs
    get() = named<Kotlin2JsCompile>("compileKotlinJs")
