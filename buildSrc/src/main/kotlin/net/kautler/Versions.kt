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

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependencyExtension

val versions = mapOf(
        // project dependencies
        "@actions/cache" to "1.0.1",
        "@actions/core" to "1.2.6",
        "@actions/exec" to "1.0.4",
        "@actions/http-client" to "1.0.8",
        "@actions/io" to "1.0.2",
        "@actions/tool-cache" to "1.6.0",
        "@types/semver" to "7.3.1",
        "kotlin-extensions" to "1.0.1-pre.110-kotlin-1.4.0",
        "kotlinx-coroutines-core" to "1.3.9",
        "kotlinx-nodejs" to "0.0.6",
        "node" to "12.18.3",
        "null-writable" to "1.0.5",
        "semver" to "7.3.2",

        // build dependencies
        "@vercel/ncc" to "0.23.0",
        "com.github.ben-manes.versions" to "0.29.0",
        "dukat" to "0.5.7",
        "github-api" to "1.116",
        "kaml" to "0.18.1",
        "kotlinx-serialization-runtime" to "0.20.0",
        "net.researchgate.release" to "2.8.1",
        "net.wooga.github" to "1.4.0",
        "org.ajoberstar.grgit" to "4.0.2",
        "org.jetbrains.kotlin.js" to "1.4.0"
)

val String.version get() = "${versions[this]}"

val String.withVersion get() = withVersion(split(":").last())

fun String.withVersion(key: String) = "$this:${key.version}"

fun DependencyHandler.npm(name: String, generateExternals: Boolean = true) =
        (extensions.getByName("npm") as NpmDependencyExtension)(name, name.version, generateExternals)
