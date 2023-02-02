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

import net.researchgate.release.GitAdapter.GitConfig
import net.researchgate.release.ReleaseExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByName

val ReleaseExtension.git
    get() = getProperty("git") as GitConfig

fun ReleaseExtension.git(configure: GitConfig.() -> Unit) =
    git.configure()

// part of work-around for https://github.com/gradle/gradle/issues/23747
val Project.release: ReleaseExtension
    get() = extensions.getByName<ReleaseExtension>("release")

fun Project.release(configure: Action<ReleaseExtension>): Unit =
    extensions.configure("release", configure)

val TaskContainer.checkoutMergeFromReleaseBranch: TaskProvider<Task>
    get() = named("checkoutMergeFromReleaseBranch")

val TaskContainer.updateVersion: TaskProvider<Task>
    get() = named("updateVersion")

val TaskContainer.afterReleaseBuild: TaskProvider<Task>
    get() = named("afterReleaseBuild")

val TaskContainer.preTagCommit: TaskProvider<Task>
    get() = named("preTagCommit")

val TaskContainer.createReleaseTag: TaskProvider<Task>
    get() = named("createReleaseTag")

val TaskContainer.beforeReleaseBuild: TaskProvider<Task>
    get() = named("beforeReleaseBuild")
