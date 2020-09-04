/*
 * Copyright 2020 Björn Kautler
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

package net.kautler

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import net.kautler.dao.GitHubWorkflow

tasks.register("preProcessTestWorkflow") {
    val input = file(".github/workflows/template/test.yml")
    val output = file(".github/workflows/test.yml")

    inputs.file(input).withPropertyName("test workflow template")
    outputs.file(output).withPropertyName("test workflow file")

    doLast {
        runCatching {
            with(Yaml(configuration = YamlConfiguration(encodeDefaults = false))) {
                val serializer = GitHubWorkflow.serializer()
                val workflow = parse(serializer, input.readText())
                workflow.jobs.remove("includes")
                stringify(serializer, workflow)
                        .also {
                            output.writeText("""
                                |# Copyright 2020 Björn Kautler
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
                                ${it.prependIndent("|")}
                            """.trimMargin())
                        }
            }
        }.onFailure {
            // work-around for https://github.com/charleskorn/kaml/issues/36
            if (it is YamlException) {
                println("Exception Location: ${it.location}")
            }
            throw it
        }
    }
}
