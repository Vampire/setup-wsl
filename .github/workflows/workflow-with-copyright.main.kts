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

@file:DependsOn("it.krzeminski:github-actions-kotlin-dsl:0.36.0")

import it.krzeminski.githubactions.domain.Concurrency
import it.krzeminski.githubactions.domain.triggers.Trigger
import it.krzeminski.githubactions.dsl.WorkflowBuilder
import it.krzeminski.githubactions.dsl.workflow
import it.krzeminski.githubactions.yaml.writeToFile
import java.io.File
import kotlin.io.path.invariantSeparatorsPathString

fun workflowWithCopyright(
    name: String,
    on: List<Trigger>,
    env: LinkedHashMap<String, String> = linkedMapOf(),
    sourceFile: File,
    concurrency: Concurrency? = null,
    block: WorkflowBuilder.() -> Unit
) {
    val sourceFilePath = sourceFile.toPath()
    workflow(
        name = name,
        on = on,
        env = env,
        sourceFile = sourceFilePath,
        concurrency = concurrency,
        block = block
    ).writeToFile(
        preamble = """
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

            This file was generated using Kotlin DSL (${sourceFilePath.invariantSeparatorsPathString.substringAfter("/setup-wsl/")}).
            If you want to modify the workflow, please change the Kotlin file and regenerate this YAML file.
            Generated with https://github.com/krzema12/github-workflows-kt
        """.trimIndent()
    )
}
