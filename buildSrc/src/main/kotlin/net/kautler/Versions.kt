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
        // project NPM dependencies
        "@actions/cache" to "1.0.4",
        "@actions/core" to "1.2.6",
        "@actions/exec" to "1.0.4",
        "@actions/http-client" to "1.0.9",
        "@actions/io" to "1.0.2",
        "@actions/tool-cache" to "1.6.1",
        "@types/semver" to "7.3.4",
        "null-writable" to "1.0.5",
        "semver" to "7.3.2",

        // project Java dependencies
        "kotlin-extensions" to "1.0.1-pre.129-kotlin-1.4.20",
        "kotlinx-coroutines-core" to "1.4.2",
        "kotlinx-nodejs" to "0.0.7",
        "node" to "12.18.3",

        // build NPM dependencies
        "@vercel/ncc" to "0.25.1",

        // build Java dependencies
        "com.github.ben-manes.versions" to "0.36.0",
        "dukat" to "0.5.7",
        "github-api" to "1.117",
        "kaml" to "0.18.1",
        "kotlinx-serialization-runtime" to "0.20.0",
        "net.researchgate.release" to "2.8.1",
        "net.wooga.github" to "1.4.0",
        "org.ajoberstar.grgit" to "4.1.0",
        "org.jetbrains.kotlin.js" to "1.4.20"
)

val String.version get() = "${versions[this]}"

val String.withVersion get() = withVersion(split(":").last())

fun String.withVersion(key: String) = "$this:${key.version}"

fun DependencyHandler.npm(name: String, generateExternals: Boolean = true) =
        (extensions.getByName("npm") as NpmDependencyExtension)(name, name.version, generateExternals)
