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

package net.kautler

import net.kautler.util.ProblemsProvider

// besides avoiding that different tasks overwrite each other result, the whole plugin is a
// work-around for https://github.com/Splitties/refreshVersions/667

if (gradle.rootBuild.startParameter.taskNames.count { it.endsWith("refreshVersions") } > 1) {
    throw extensions.create<ProblemsProvider>("problemsProvider").problems.reporter.throwing(
        IllegalArgumentException(),
        ProblemId.create(
            "only-one-refreshversions-task-can-be-executed-per-gradle-run",
            "Multiple refreshVersions tasks requested",
            ProblemGroup.create("build-authoring", "Build Authoring")
        )
    ) {
        solution("Only request one refreshVersions task per Gradle run")
        severity(Severity.ERROR)
    }
}

onRefreshVersionsRequested {
    apply(plugin = "de.fayard.refreshVersions")
}
