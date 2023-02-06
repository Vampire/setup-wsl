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

import de.fayard.refreshVersions.RefreshVersionsExtension
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.kotlin.dsl.refreshVersions

inline fun Settings.onRefreshVersionsRequested(configure: Settings.() -> Unit) {
    val rootBuild = gradle.parent == null
    val startTaskNames = gradle.rootBuild.startParameter.taskNames
    if (
        (rootBuild && (startTaskNames.contains(":refreshVersions") || startTaskNames.contains("refreshVersions")))
        || (!rootBuild && startTaskNames.contains(":${settings.rootProject.name}:refreshVersions"))
    ) {
        configure()
    }
}

inline fun Settings.conditionalRefreshVersions(configure: RefreshVersionsExtension.() -> Unit) {
    onRefreshVersionsRequested {
        refreshVersions(configure)
    }
}

val Gradle.rootBuild: Gradle
    get() = if (gradle.parent == null) gradle else gradle.parent!!.rootBuild
