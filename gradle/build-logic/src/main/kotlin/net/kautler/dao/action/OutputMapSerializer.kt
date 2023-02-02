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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialKind.CONTEXTUAL
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.kautler.dao.action.Output.CompositeOutput
import net.kautler.dao.action.Output.NormalOutput

@ExperimentalSerializationApi
@Serializer(forClass = Map::class)
class OutputMapSerializer(
    private val keySerializer: KSerializer<String>,
    private val valueSerializer: KSerializer<Output>
) : KSerializer<Map<String, Output>> {
    @InternalSerializationApi
    override val descriptor =
        buildSerialDescriptor("net.kautler.dao.action.GitHubAction.outputs", CONTEXTUAL)

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
