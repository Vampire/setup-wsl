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
@file:DependsOn("Vampire:setup-wsl:RELEASE")

import io.github.typesafegithub.workflows.actions.vampire.SetupWsl
import io.github.typesafegithub.workflows.domain.RunnerType.WindowsLatest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch

workflowWithCopyright(
    name = "Verify Release",
    on = listOf(
        Push(tags = listOf("v*")),
        WorkflowDispatch()
    ),
    sourceFile = __FILE__
) {
    job(
        id = "verify_release",
        name = "Verify Release",
        runsOn = WindowsLatest
    ) {
        uses(
            name = "Execute action",
            action = SetupWsl()
        )
    }
}
