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

@file:DependsOn("io.github.typesafegithub:github-workflows-kt:2.0.0")

import io.github.typesafegithub.workflows.domain.Concurrency
import io.github.typesafegithub.workflows.domain.triggers.Trigger
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.Preamble.WithOriginalAfter
import java.io.File

fun workflowWithCopyright(
    name: String,
    on: List<Trigger>,
    env: Map<String, String> = mapOf(),
    sourceFile: File,
    concurrency: Concurrency? = null,
    block: WorkflowBuilder.() -> Unit
) {
    workflow(
        name = name,
        on = on,
        env = env,
        sourceFile = sourceFile,
        concurrency = concurrency,
        preamble = WithOriginalAfter(
            """
                Copyright 2020-2023 Björn Kautler

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
