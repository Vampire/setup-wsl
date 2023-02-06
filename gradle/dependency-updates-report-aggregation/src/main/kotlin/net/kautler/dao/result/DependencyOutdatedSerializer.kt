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

import com.github.benmanes.gradle.versions.reporter.result.DependencyOutdated
import com.github.benmanes.gradle.versions.reporter.result.VersionAvailable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

object DependencyOutdatedSerializer : KSerializer<DependencyOutdated> {
    override val descriptor = buildClassSerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.DependencyOutdated") {
        element<String?>("group")
        element<String?>("name")
        element<String?>("version")
        element<String?>("projectUrl")
        element<String?>("userReason")
        element("available", VersionAvailableSerializer.descriptor)
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var group: String? = null
        var name: String? = null
        var version: String? = null
        var projectUrl: String? = null
        var userReason: String? = null
        var available: VersionAvailable? = null

        while (true) {
            @OptIn(ExperimentalSerializationApi::class)
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break
                0 -> group = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                1 -> name = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                2 -> version = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                3 -> projectUrl = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                4 -> userReason = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                5 -> available = decodeSerializableElement(descriptor, index, VersionAvailableSerializer)
                else -> error("Unexpected index $index")
            }
        }

        listOf(
            "available" to available
        )
            .filter { it.second == null }
            .map { it.first }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.also {
                @OptIn(ExperimentalSerializationApi::class)
                throw MissingFieldException(it, descriptor.serialName)
            }

        DependencyOutdated(group, name, version, projectUrl, userReason, available!!)
    }

    override fun serialize(encoder: Encoder, value: DependencyOutdated) {
        @OptIn(ExperimentalSerializationApi::class)
        encoder.encodeStructure(descriptor) {
            encodeNullableSerializableElement(descriptor, 0, String.serializer(), value.group)
            encodeNullableSerializableElement(descriptor, 1, String.serializer(), value.name)
            encodeNullableSerializableElement(descriptor, 2, String.serializer(), value.version)
            encodeNullableSerializableElement(descriptor, 3, String.serializer(), value.projectUrl)
            encodeNullableSerializableElement(descriptor, 4, String.serializer(), value.userReason)
            encodeSerializableElement(descriptor, 5, VersionAvailableSerializer, value.available)
        }
    }
}
