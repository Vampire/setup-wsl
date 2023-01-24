#!/usr/bin/env kotlin

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

@file:DependsOn("it.krzeminski:github-actions-kotlin-dsl:0.35.0")

import it.krzeminski.githubactions.actions.CustomAction
import it.krzeminski.githubactions.actions.actions.CheckoutV2
import it.krzeminski.githubactions.domain.RunnerType.WindowsLatest
import it.krzeminski.githubactions.domain.triggers.Cron
import it.krzeminski.githubactions.domain.triggers.Schedule
import it.krzeminski.githubactions.dsl.workflow
import it.krzeminski.githubactions.yaml.writeToFile

workflow(
    name = "Check Dependency Versions",
    on = listOf(
        Schedule(
            listOf(
                Cron(
                    minute = "0",
                    hour = "0",
                    // work-around for https://github.com/krzema12/github-workflows-kt/issues/642
                    // use FRI after the issue is fixed
                    dayWeek = "5"
                )
            )
        )
    ),
    sourceFile = __FILE__.toPath()
) {
    job(
        id = "check_dependency_versions",
        name = "Check Dependency Versions",
        runsOn = WindowsLatest
    ) {
        run(
            name = "Configure Git",
            command = "git config --global core.autocrlf input"
        )
        uses(
            name = "Checkout",
            action = CheckoutV2()
        )
        uses(
            name = "Check Dependency Versions",
            action = CustomAction(
                actionOwner = "burrunan",
                actionName = "gradle-cache-action",
                actionVersion = "v1",
                inputs = mapOf(
                    "arguments" to "dependencyUpdates",
                    "debug" to "false",
                    "concurrent" to "true",
                    "gradle-dependencies-cache-key" to "buildSrc/**/Versions.kt"
                )
            )
        )
    }
}.apply {
    writeToFile()
    __FILE__.resolveSibling(targetFileName).apply {
        writeText(
            """
                |# Copyright 2020-2023 Björn Kautler
                |#
                |# Licensed under the Apache License, Version 2.0 (the "License");
                |# you may not use this file except in compliance with the License.
                |# You may obtain a copy of the License at
                |#
                |#     http://www.apache.org/licenses/LICENSE-2.0
                |#
                |# Unless required by applicable law or agreed to in writing, software
                |# distributed under the License is distributed on an "AS IS" BASIS,
                |# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                |# See the License for the specific language governing permissions and
                |# limitations under the License.
                |
                ${readText().prependIndent("|")}
            """.trimMargin()
        )
    }
}
