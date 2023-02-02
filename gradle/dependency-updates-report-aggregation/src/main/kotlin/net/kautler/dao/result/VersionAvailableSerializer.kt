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

import com.github.benmanes.gradle.versions.reporter.result.VersionAvailable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
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
@Serializer(forClass = VersionAvailable::class)
object VersionAvailableSerializer : KSerializer<VersionAvailable> {
    override val descriptor = buildClassSerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.VersionAvailable") {
        element<String?>("release")
        element<String?>("milestone")
        element<String?>("integration")
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var release: String? = null
        var milestone: String? = null
        var integration: String? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break
                0 -> release = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                1 -> milestone = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                2 -> integration = decodeNullableSerializableElement(descriptor, index, String.serializer().nullable)
                else -> error("Unexpected index $index")
            }
        }

        VersionAvailable(release, milestone, integration)
    }

    override fun serialize(encoder: Encoder, value: VersionAvailable) {
        encoder.encodeStructure(descriptor) {
            encodeNullableSerializableElement(descriptor, 0, String.serializer(), value.release)
            encodeNullableSerializableElement(descriptor, 1, String.serializer(), value.milestone)
            encodeNullableSerializableElement(descriptor, 2, String.serializer(), value.integration)
        }
    }
}
