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

import com.github.benmanes.gradle.versions.reporter.result.DependenciesGroup
import com.github.benmanes.gradle.versions.reporter.result.Dependency
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import java.util.SortedSet

@ExperimentalSerializationApi
@Serializer(forClass = DependenciesGroup::class)
class DependenciesGroupSerializer<E : Dependency>(
    private val elementSerializer: KSerializer<E>
) : KSerializer<DependenciesGroup<E>> {
    override val descriptor = buildClassSerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.DependenciesGroup") {
        element<Int>("count")
        element("dependencies", SetSerializer(elementSerializer).descriptor)
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var count: Int? = null
        var dependencies: SortedSet<E>? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break
                0 -> count = decodeIntElement(descriptor, index)
                1 -> dependencies = decodeSerializableElement(descriptor, index, SetSerializer(elementSerializer)).toSortedSet()
                else -> error("Unexpected index $index")
            }
        }

        listOf(
            "count" to count,
            "dependencies" to dependencies
        )
            .filter { it.second == null }
            .map { it.first }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.also { throw MissingFieldException(it, descriptor.serialName) }

        DependenciesGroup(count!!, dependencies)
    }

    override fun serialize(encoder: Encoder, value: DependenciesGroup<E>) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.count)
            encodeSerializableElement(descriptor, 1, SetSerializer(elementSerializer), value.dependencies)
        }
    }
}
