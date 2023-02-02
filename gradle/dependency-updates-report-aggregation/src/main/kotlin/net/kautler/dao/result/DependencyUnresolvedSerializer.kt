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

package net.kautler.dao.result

import com.github.benmanes.gradle.versions.reporter.result.DependencyUnresolved
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@ExperimentalSerializationApi
@Serializer(forClass = DependencyUnresolved::class)
object DependencyUnresolvedSerializer : KSerializer<DependencyUnresolved> {
    override val descriptor = buildClassSerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.DependencyUnresolved") {
        element<String>("group")
        element<String>("name")
        element<String>("version")
        element<String?>("projectUrl")
        element<String>("reason")
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var group: String? = null
        var name: String? = null
        var version: String? = null
        var projectUrl: String? = null
        var reason: String? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break
                0 -> group = decodeStringElement(descriptor, index)
                1 -> name = decodeStringElement(descriptor, index)
                2 -> version = decodeStringElement(descriptor, index)
                3 -> projectUrl = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                4 -> reason = decodeStringElement(descriptor, index)
                else -> error("Unexpected index $index")
            }
        }

        listOf(
            "group" to group,
            "name" to name,
            "version" to version,
            "reason" to reason
        )
            .filter { it.second == null }
            .map { it.first }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.also { throw MissingFieldException(it, descriptor.serialName) }

        DependencyUnresolved(group, name, version, projectUrl, reason)
    }

    override fun serialize(encoder: Encoder, value: DependencyUnresolved) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.group)
            encodeStringElement(descriptor, 1, value.name)
            encodeStringElement(descriptor, 2, value.version)
            encodeNullableSerializableElement(descriptor, 3, String.serializer(), value.projectUrl)
            encodeStringElement(descriptor, 4, value.reason)
        }
    }
}
