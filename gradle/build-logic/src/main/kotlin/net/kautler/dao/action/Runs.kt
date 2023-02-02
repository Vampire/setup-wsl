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

import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
        val steps: List<Step>
    ) : Runs() {
        @ExperimentalSerializationApi
        @EncodeDefault
        override val using: String = "composite"
    }

    @Serializable
    data class DockerRuns(
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
    ) : Runs() {
        @ExperimentalSerializationApi
        @EncodeDefault
        override val using: String = "docker"
    }

    @ExperimentalSerializationApi
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
