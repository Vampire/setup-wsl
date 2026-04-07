/*
 * Copyright 2020-2026 Björn Kautler
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

import net.researchgate.release.ReleaseExtension
import net.researchgate.release.ReleasePlugin.getRELEASE_GROUP
import net.researchgate.release.tasks.CreateReleaseTag
import net.researchgate.release.tasks.PreTagCommit
import net.researchgate.release.tasks.UpdateVersion
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

// part of work-around for https://github.com/researchgate/gradle-release/issues/304
val Project.release: ReleaseExtension
    get() = extensions.getByName<ReleaseExtension>("release")

fun Project.release(configure: Action<ReleaseExtension>): Unit =
    extensions.configure("release", configure)

val TaskContainer.release: TaskProvider<GradleBuild>
    get() = named<GradleBuild>("release")

val TaskContainer.runBuildTasks: TaskProvider<GradleBuild>
    get() = named<GradleBuild>("runBuildTasks")

val TaskContainer.checkoutMergeFromReleaseBranch: TaskProvider<Task>
    get() = named("checkoutMergeFromReleaseBranch")

val TaskContainer.checkoutMergeToReleaseBranch: TaskProvider<Task>
    get() = named("checkoutMergeToReleaseBranch")

val TaskContainer.updateVersion: TaskProvider<UpdateVersion>
    get() = named<UpdateVersion>("updateVersion")

val TaskContainer.afterReleaseBuild: TaskProvider<Task>
    get() = named("afterReleaseBuild")

val TaskContainer.preTagCommit: TaskProvider<PreTagCommit>
    get() = named<PreTagCommit>("preTagCommit")

val TaskContainer.createReleaseTag: TaskProvider<CreateReleaseTag>
    get() = named<CreateReleaseTag>("createReleaseTag")

val TaskContainer.beforeReleaseBuild: TaskProvider<Task>
    get() = named("beforeReleaseBuild")

// part of work-around for https://github.com/researchgate/gradle-release/pull/405
inline fun <reified T : Task> TaskContainer.registerMockTask(name: String) = register<T>(name) {
    group = getRELEASE_GROUP()
    // fail as soon as the task gets configured except while IntelliJ IDEA sync
    if (!System.getProperty("idea.sync.active").toBoolean()) {
        error("Please disable the configuration cache to use release tasks")
    }
}
