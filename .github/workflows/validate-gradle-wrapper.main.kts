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
@file:DependsOn("gradle:wrapper-validation-action___major:[v1,v2-alpha)")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.gradle.WrapperValidationAction
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push

workflowWithCopyright(
    name = "Validate Gradle Wrapper",
    on = listOf(
        Push(),
        PullRequest()
    ),
    sourceFile = __FILE__
) {
    job(
        id = "validate_gradle_wrapper",
        name = "Validate Gradle Wrapper",
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
            name = "Validate Gradle Wrapper",
            action = WrapperValidationAction()
        )
    }
}
