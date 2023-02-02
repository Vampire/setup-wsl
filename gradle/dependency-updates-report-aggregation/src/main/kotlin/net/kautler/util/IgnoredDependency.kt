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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.DomainObjectSet
import org.gradle.kotlin.dsl.getByName

data class IgnoredDependency(
    val group: String,
    val name: String,
    val oldVersion: String? = null,
    val newVersion: String? = null
)

val DependencyUpdatesTask.ignoredDependencies: DomainObjectCollection<IgnoredDependency>
    get() = extensions.getByName<DomainObjectSet<IgnoredDependency>>("ignoredDependencies")

fun DependencyUpdatesTask.ignoredDependencies(configure: Action<DomainObjectCollection<IgnoredDependency>>): Unit =
    extensions.configure("ignoredDependencies", configure)

fun DomainObjectCollection<IgnoredDependency>.add(
    group: String,
    name: String,
    oldVersion: String? = null,
    newVersion: String? = null
) = add(
    IgnoredDependency(
        group = group,
        name = name,
        oldVersion = oldVersion,
        newVersion = newVersion
    )
)
