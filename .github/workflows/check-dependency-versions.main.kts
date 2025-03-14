#!/usr/bin/env kotlin

/*
 * Copyright 2020-2025 Björn Kautler
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

@file:Repository("https://repo.maven.apache.org/maven2/")
// work-around for https://youtrack.jetbrains.com/issue/KT-69145
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.2.0")

@file:Repository("https://bindings.krzeminski.it/")
@file:DependsOn("actions:checkout___major:[v4,v5-alpha)")
@file:DependsOn("actions:setup-java___major:[v4,v5-alpha)")
@file:DependsOn("burrunan:gradle-cache-action___major:[v1,v2-alpha)")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.actions.SetupJava.Distribution.Temurin
import io.github.typesafegithub.workflows.actions.burrunan.GradleCacheAction
import io.github.typesafegithub.workflows.domain.RunnerType.WindowsLatest
import io.github.typesafegithub.workflows.domain.triggers.Cron
import io.github.typesafegithub.workflows.domain.triggers.Schedule

workflowWithCopyright(
    name = "Check Dependency Versions",
    on = listOf(
        Schedule(
            listOf(
                Cron(
                    minute = "0",
                    hour = "0",
                    dayWeek = "FRI"
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
            action = Checkout()
        )
        uses(
            name = "Setup Java 11",
            action = SetupJava(
                javaVersion = "11",
                distribution = Temurin
            )
        )
        uses(
            name = "Check Dependency Versions",
            action = GradleCacheAction(
                arguments = listOf("--show-version", "dependencyUpdates"),
                debug = false,
                concurrent = true
            )
        )
    }
}
