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
@file:Repository("https://bindings.krzeminski.it/")

@file:Repository("https://repo.maven.apache.org/maven2/")
// work-around for https://youtrack.jetbrains.com/issue/KT-69145
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.2.0")

@file:DependsOn("actions:checkout___major:[v4,v5-alpha)")
@file:DependsOn("typesafegithub:github-actions-typing___major:[v2,v3-alpha)")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.typesafegithub.GithubActionsTyping
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push

// comment in for editability with IntelliSense
//fun workflowWithCopyright(
//    name: String,
//    on: List<io.github.typesafegithub.workflows.domain.triggers.Trigger>,
//    sourceFile: java.io.File,
//    block: io.github.typesafegithub.workflows.dsl.WorkflowBuilder.() -> Unit
//) = Unit

workflowWithCopyright(
    name = "Check Action Typing",
    on = listOf(
        Push(),
        PullRequest()
    ),
    sourceFile = __FILE__
) {
    job(
        id = "check_action_typing",
        name = "Check Action Typing",
        runsOn = UbuntuLatest
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
            name = "Check Action Typing",
            action = GithubActionsTyping()
        )
    }
}
