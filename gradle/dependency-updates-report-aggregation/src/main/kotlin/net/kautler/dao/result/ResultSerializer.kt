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
import com.github.benmanes.gradle.versions.reporter.result.DependencyLatest
import com.github.benmanes.gradle.versions.reporter.result.DependencyOutdated
import com.github.benmanes.gradle.versions.reporter.result.DependencyUnresolved
import com.github.benmanes.gradle.versions.reporter.result.Result
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@ExperimentalSerializationApi
@Serializer(forClass = Result::class)
object ResultSerializer : KSerializer<Result> {
    override val descriptor = buildClassSerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.Result") {
        element<Int>("count")
        element("current", DependenciesGroupSerializer(DependencySerializer).descriptor)
        element("outdated", DependenciesGroupSerializer(DependencyOutdatedSerializer).descriptor)
        element("exceeded", DependenciesGroupSerializer(DependencyLatestSerializer).descriptor)
        element("unresolved", DependenciesGroupSerializer(DependencyUnresolvedSerializer).descriptor)
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var count: Int? = null
        var current: DependenciesGroup<Dependency>? = null
        var outdated: DependenciesGroup<DependencyOutdated>? = null
        var exceeded: DependenciesGroup<DependencyLatest>? = null
        var unresolved: DependenciesGroup<DependencyUnresolved>? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break
                0 -> count = decodeIntElement(descriptor, index)
                1 -> current = decodeSerializableElement(descriptor, index, DependenciesGroupSerializer(DependencySerializer))
                2 -> outdated = decodeSerializableElement(descriptor, index, DependenciesGroupSerializer(DependencyOutdatedSerializer))
                3 -> exceeded = decodeSerializableElement(descriptor, index, DependenciesGroupSerializer(DependencyLatestSerializer))
                4 -> unresolved = decodeSerializableElement(descriptor, index, DependenciesGroupSerializer(DependencyUnresolvedSerializer))
                else -> error("Unexpected index $index")
            }
        }

        listOf(
            "count" to count,
            "current" to current,
            "outdated" to outdated,
            "exceeded" to exceeded,
            "unresolved" to unresolved
        )
            .filter { it.second == null }
            .map { it.first }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.also { throw MissingFieldException(it, descriptor.serialName) }

        Result(count!!, current, outdated, exceeded, unresolved)
    }

    override fun serialize(encoder: Encoder, value: Result) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.count)
            encodeSerializableElement(descriptor, 1, DependenciesGroupSerializer(DependencySerializer), value.current)
            encodeSerializableElement(descriptor, 2, DependenciesGroupSerializer(DependencyOutdatedSerializer), value.outdated)
            encodeSerializableElement(descriptor, 3, DependenciesGroupSerializer(DependencyLatestSerializer), value.exceeded)
            encodeSerializableElement(descriptor, 4, DependenciesGroupSerializer(DependencyUnresolvedSerializer), value.unresolved)
        }
    }
}
