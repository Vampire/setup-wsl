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

package net.kautler.dao

import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.UnionKind.CONTEXTUAL
import kotlinx.serialization.builtins.MapSerializer
import net.kautler.dao.GitHubAction.Output.OutputMapSerializer
import net.kautler.util.get

@Serializable
data class GitHubAction(
        val name: String,
        val author: String? = null,
        val description: String,
        val inputs: Map<String, Input>? = null,
        @Serializable(with = OutputMapSerializer::class)
        val outputs: Map<String, Output>? = null,
        val runs: Runs,
        val branding: Branding
) {
    @Serializable
    data class Input(
            val description: String,
            val required: Boolean,
            val default: String? = null
    )

    @Serializable
    sealed class Output {
        abstract val description: String

        @Serializable
        data class NormalOutput(
                override val description: String
        ) : Output()

        @Serializable
        data class CompositeOutput(
                override val description: String,
                val value: String
        ) : Output()

        @Serializer(forClass = Map::class)
        class OutputMapSerializer(
                private val keySerializer: KSerializer<String>,
                private val valueSerializer: KSerializer<Output>
        ) : KSerializer<Map<String, Output>> {
            override val descriptor =
                    SerialDescriptor("net.kautler.dao.GitHubAction.outputs", CONTEXTUAL)

            override fun deserialize(decoder: Decoder): Map<String, Output> {
                check(decoder is YamlInput) { "This class can only be loaded using kaml" }

                val context = decoder.node
                check(context is YamlMap) { "Expected a YamlMap as current context" }

                val compositeAction = context
                        .get<YamlMap>("runs")
                        ?.get<YamlScalar>("using")
                        ?.content == "composite"

                val valueSerializer =
                        if (compositeAction) CompositeOutput.serializer()
                        else NormalOutput.serializer()

                return decoder.decodeSerializableValue(MapSerializer(keySerializer, valueSerializer))
            }

            override fun serialize(encoder: Encoder, value: Map<String, Output>) {
                @Suppress("UNCHECKED_CAST")
                when (value.values.asSequence().map { it::class }.distinct().count()) {
                    0 -> encoder.encodeSerializableValue(MapSerializer(keySerializer, valueSerializer), value)

                    1 -> when (value.values.first()) {
                        is NormalOutput -> encoder.encodeSerializableValue(
                                MapSerializer(keySerializer, NormalOutput.serializer()),
                                value as Map<String, NormalOutput>
                        )

                        is CompositeOutput -> encoder.encodeSerializableValue(
                                MapSerializer(keySerializer, CompositeOutput.serializer()),
                                value as Map<String, CompositeOutput>
                        )
                    }

                    else -> error("Output map must not contain normal and composite outputs at the same time")
                }
            }
        }
    }

    sealed class Runs {
        abstract val using: String

        @Serializable
        data class JavaScriptRuns(
                override val using: String,
                val main: String,
                val pre: String? = null,
                @SerialName("pre-if")
                val preIf: String? = null,
                val post: String? = null,
                @SerialName("post-if")
                val postIf: String? = null
        ) : Runs()

        @Serializable
        data class CompositeRuns(
                override val using: String,
                val steps: List<Step>
        ) : Runs()

        @Serializable
        data class DockerRuns(
                override val using: String,
                @SerialName("pre-entrypoint")
                val preEntrypoint: String? = null,
                @SerialName("pre-if")
                val preIf: String? = null,
                val image: String,
                val env: Map<String, String>? = null,
                val entrypoint: String? = null,
                @SerialName("post-entrypoint")
                val postEntrypoint: String? = null,
                @SerialName("post-if")
                val postIf: String? = null,
                val args: List<String>? = null
        ) : Runs()

        @Serializer(forClass = Runs::class)
        companion object : KSerializer<Runs> {
            override fun deserialize(decoder: Decoder): Runs {
                check(decoder is YamlInput) { "This class can only be loaded using kaml" }

                val context = decoder.node
                check(context is YamlMap) { "Expected a YamlMap as current context" }

                val actionType = context
                        .get<YamlMap>("runs")
                        ?.get<YamlScalar>("using")
                        ?.content

                val valueSerializer = when (actionType) {
                    "composite" -> CompositeRuns.serializer()
                    "docker" -> DockerRuns.serializer()
                    else -> JavaScriptRuns.serializer()
                }

                return decoder.decodeSerializableValue(valueSerializer)
            }

            override fun serialize(encoder: Encoder, value: Runs) {
                when (value) {
                    is JavaScriptRuns -> encoder.encodeSerializableValue(JavaScriptRuns.serializer(), value)
                    is CompositeRuns -> encoder.encodeSerializableValue(CompositeRuns.serializer(), value)
                    is DockerRuns -> encoder.encodeSerializableValue(DockerRuns.serializer(), value)
                }
            }
        }
    }

    @Serializable
    data class Step(
            val run: String,
            val shell: String,
            val name: String? = null,
            val id: String? = null,
            val env: Map<String, String>? = null,
            @SerialName("working-directory")
            val workingDirectory: String? = null
    )

    @Serializable
    data class Branding(
            val color: String,
            val icon: String
    )
}
