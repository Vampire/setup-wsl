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

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")

@file:Repository("https://bindings.krzeminski.it/")
@file:DependsOn("fwilhe2:setup-kotlin___major:[v1,v2-alpha)")

import io.github.typesafegithub.workflows.actions.fwilhe2.SetupKotlin
import io.github.typesafegithub.workflows.domain.Concurrency
import io.github.typesafegithub.workflows.domain.triggers.Trigger
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import io.github.typesafegithub.workflows.dsl.expressions.Contexts.github
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.CheckoutActionVersionSource.InferFromClasspath
import io.github.typesafegithub.workflows.yaml.DEFAULT_CONSISTENCY_CHECK_JOB_CONFIG
import io.github.typesafegithub.workflows.yaml.Preamble.WithOriginalAfter
import java.io.File

fun workflowWithCopyright(
    name: String,
    on: List<Trigger>,
    env: Map<String, String> = mapOf(),
    sourceFile: File,
    block: WorkflowBuilder.() -> Unit
) {
    workflow(
        name = name,
        on = on,
        env = env,
        sourceFile = sourceFile,
        concurrency = Concurrency(
            group = "${expr { github.workflow }}-${expr("${github.eventPullRequest.pull_request.number} || ${github.ref}")}",
            cancelInProgress = true
        ),
        consistencyCheckJobConfig = DEFAULT_CONSISTENCY_CHECK_JOB_CONFIG.copy(
            checkoutActionVersion = InferFromClasspath(),
            additionalSteps = {
                // work-around for https://youtrack.jetbrains.com/issue/KT-86352
                uses(
                    name = "Install Kotlin 2.3.10",
                    action = SetupKotlin(version = "2.3.10")
                )
            }
        ),
        preamble = WithOriginalAfter(
            """
                Copyright 2020-2026 Björn Kautler

                Licensed under the Apache License, Version 2.0 (the "License");
                you may not use this file except in compliance with the License.
                You may obtain a copy of the License at

                    http://www.apache.org/licenses/LICENSE-2.0

                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                See the License for the specific language governing permissions and
                limitations under the License.
            """.trimIndent()
        ),
        block = block
    )
}
