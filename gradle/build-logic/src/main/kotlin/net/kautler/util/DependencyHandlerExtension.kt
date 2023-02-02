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

package net.kautler.util

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependency
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmDependencyExtension

fun DependencyHandler.npm(
    dependency: Provider<MinimalExternalModuleDependency>,
    generateExternals: Boolean = true
): NpmDependency {
    val dep = dependency.get()
    return (extensions.getByName("npm") as NpmDependencyExtension)(
        name = if (dep.group == "<unscoped>") dep.name else "@${dep.group}/${dep.name}",
        version = dep.version!!,
        generateExternals = generateExternals
    )
}
