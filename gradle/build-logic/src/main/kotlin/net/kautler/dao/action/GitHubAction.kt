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

package net.kautler.dao.action

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import net.kautler.dao.action.Output.CompositeOutput
import net.kautler.dao.action.Output.NormalOutput

@ExperimentalSerializationApi
@Serializable
data class GitHubAction(
    val name: String,
    val author: String? = null,
    val description: String,
    val inputs: Map<String, Input>? = null,
    @Serializable(with = OutputMapSerializer::class)
    val outputs: Map<String, Output>? = null,
    val runs: Runs,
    val branding: Branding? = null
) {
    init {
        require((runs.using == "composite") || (outputs.orEmpty().values.all { it is NormalOutput })) {
            "Non-composite actions must only contain normal outputs"
        }
        require((runs.using != "composite") || (outputs.orEmpty().values.all { it is CompositeOutput })) {
            "Composite actions must only contain composite outputs"
        }
    }
}
