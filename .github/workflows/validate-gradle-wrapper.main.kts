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

import it.krzeminski.githubactions.actions.actions.CheckoutV2
import it.krzeminski.githubactions.actions.gradle.WrapperValidationActionV1
import it.krzeminski.githubactions.domain.RunnerType.UbuntuLatest
import it.krzeminski.githubactions.domain.triggers.PullRequest
import it.krzeminski.githubactions.domain.triggers.Push

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
            action = CheckoutV2()
        )
        uses(
            name = "Validate Gradle Wrapper",
            action = WrapperValidationActionV1()
        )
    }
}
