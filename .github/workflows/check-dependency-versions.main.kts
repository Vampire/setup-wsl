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

@file:Import("workflow-with-copyright.main.kts")

import it.krzeminski.githubactions.actions.actions.CheckoutV3
import it.krzeminski.githubactions.actions.burrunan.GradleCacheActionV1
import it.krzeminski.githubactions.domain.RunnerType.WindowsLatest
import it.krzeminski.githubactions.domain.triggers.Cron
import it.krzeminski.githubactions.domain.triggers.Schedule

workflowWithCopyright(
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
    sourceFile = __FILE__
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
            action = CheckoutV3()
        )
        uses(
            name = "Check Dependency Versions",
            action = GradleCacheActionV1(
                arguments = listOf("--show-version", "dependencyUpdates"),
                debug = false,
                concurrent = true
            )
        )
    }
}
