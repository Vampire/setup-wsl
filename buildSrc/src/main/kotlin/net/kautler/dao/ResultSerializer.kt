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

import com.github.benmanes.gradle.versions.reporter.result.DependenciesGroup
import com.github.benmanes.gradle.versions.reporter.result.Dependency
import com.github.benmanes.gradle.versions.reporter.result.DependencyLatest
import com.github.benmanes.gradle.versions.reporter.result.DependencyOutdated
import com.github.benmanes.gradle.versions.reporter.result.DependencyUnresolved
import com.github.benmanes.gradle.versions.reporter.result.Result
import com.github.benmanes.gradle.versions.reporter.result.VersionAvailable
import kotlinx.serialization.CompositeDecoder.Companion.READ_DONE
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.set
import kotlinx.serialization.decodeStructure
import kotlinx.serialization.encodeStructure
import java.util.SortedSet

@Serializer(forClass = Result::class)
object ResultSerializer : KSerializer<Result> {
    override val descriptor = SerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.Result") {
        element("count", Int.serializer().descriptor)
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

        loop@ while (true) {
            when (val i = decodeElementIndex(descriptor)) {
                READ_DONE -> break@loop
                0 -> count = decodeIntElement(descriptor, i)
                1 -> current = decodeSerializableElement(descriptor, i, DependenciesGroupSerializer(DependencySerializer))
                2 -> outdated = decodeSerializableElement(descriptor, i, DependenciesGroupSerializer(DependencyOutdatedSerializer))
                3 -> exceeded = decodeSerializableElement(descriptor, i, DependenciesGroupSerializer(DependencyLatestSerializer))
                4 -> unresolved = decodeSerializableElement(descriptor, i, DependenciesGroupSerializer(DependencyUnresolvedSerializer))
                else -> throw SerializationException("Unknown index $i")
            }
        }

        Result(count ?: throw MissingFieldException("count"), current, outdated, exceeded, unresolved)
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

    @Serializer(forClass = DependenciesGroup::class)
    class DependenciesGroupSerializer<E : Dependency>(
            private val elementSerializer: KSerializer<E>
    ) : KSerializer<DependenciesGroup<E>> {
        override val descriptor = SerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.DependenciesGroup") {
            element("count", Int.serializer().descriptor)
            element("dependencies", elementSerializer.set.descriptor)
        }

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
            var count: Int? = null
            var dependencies: SortedSet<E>? = null

            loop@ while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    READ_DONE -> break@loop
                    0 -> count = decodeIntElement(descriptor, i)
                    1 -> dependencies = decodeSerializableElement(descriptor, i, elementSerializer.set).toSortedSet()
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            DependenciesGroup(count ?: throw MissingFieldException("count"), dependencies)
        }

        override fun serialize(encoder: Encoder, value: DependenciesGroup<E>) {
            encoder.encodeStructure(descriptor) {
                encodeIntElement(descriptor, 0, value.count)
                encodeSerializableElement(descriptor, 1, elementSerializer.set, value.dependencies)
            }
        }
    }

    @Serializer(forClass = Dependency::class)
    object DependencySerializer : KSerializer<Dependency> {
        override val descriptor = SerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.Dependency") {
            element("group", String.serializer().descriptor)
            element("name", String.serializer().descriptor)
            element("version", String.serializer().descriptor)
            element("projectUrl", String.serializer().nullable.descriptor)
        }

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
            var group: String? = null
            var name: String? = null
            var version: String? = null
            var projectUrl: String? = null

            loop@ while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    READ_DONE -> break@loop
                    0 -> group = decodeStringElement(descriptor, i)
                    1 -> name = decodeStringElement(descriptor, i)
                    2 -> version = decodeStringElement(descriptor, i)
                    3 -> projectUrl = decodeNullableSerializableElement(descriptor, i, String.serializer().nullable)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            Dependency(group, name, version, projectUrl)
        }

        override fun serialize(encoder: Encoder, value: Dependency) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.group)
                encodeStringElement(descriptor, 1, value.name)
                encodeStringElement(descriptor, 2, value.version)
                encodeNullableSerializableElement(descriptor, 3, String.serializer().nullable, value.projectUrl)
            }
        }
    }

    @Serializer(forClass = DependencyOutdated::class)
    object DependencyOutdatedSerializer : KSerializer<DependencyOutdated> {
        override val descriptor = SerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.DependencyOutdated") {
            element("group", String.serializer().descriptor)
            element("name", String.serializer().descriptor)
            element("version", String.serializer().descriptor)
            element("projectUrl", String.serializer().nullable.descriptor)
            element("available", VersionAvailableSerializer.descriptor)
        }

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
            var group: String? = null
            var name: String? = null
            var version: String? = null
            var projectUrl: String? = null
            var available: VersionAvailable? = null

            loop@ while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    READ_DONE -> break@loop
                    0 -> group = decodeStringElement(descriptor, i)
                    1 -> name = decodeStringElement(descriptor, i)
                    2 -> version = decodeStringElement(descriptor, i)
                    3 -> projectUrl = decodeNullableSerializableElement(descriptor, i, String.serializer().nullable)
                    4 -> available = decodeSerializableElement(descriptor, i, VersionAvailableSerializer)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            DependencyOutdated(group, name, version, projectUrl, available)
        }

        override fun serialize(encoder: Encoder, value: DependencyOutdated) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.group)
                encodeStringElement(descriptor, 1, value.name)
                encodeStringElement(descriptor, 2, value.version)
                encodeNullableSerializableElement(descriptor, 3, String.serializer().nullable, value.projectUrl)
                encodeSerializableElement(descriptor, 4, VersionAvailableSerializer, value.available)
            }
        }
    }

    @Serializer(forClass = DependencyLatest::class)
    object DependencyLatestSerializer : KSerializer<DependencyLatest> {
        override val descriptor = SerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.DependencyLatest") {
            element("group", String.serializer().descriptor)
            element("name", String.serializer().descriptor)
            element("version", String.serializer().descriptor)
            element("projectUrl", String.serializer().nullable.descriptor)
            element("latest", String.serializer().descriptor)
        }

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
            var group: String? = null
            var name: String? = null
            var version: String? = null
            var projectUrl: String? = null
            var latest: String? = null

            loop@ while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    READ_DONE -> break@loop
                    0 -> group = decodeStringElement(descriptor, i)
                    1 -> name = decodeStringElement(descriptor, i)
                    2 -> version = decodeStringElement(descriptor, i)
                    3 -> projectUrl = decodeNullableSerializableElement(descriptor, i, String.serializer().nullable)
                    4 -> latest = decodeStringElement(descriptor, i)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            DependencyLatest(group, name, version, projectUrl, latest)
        }

        override fun serialize(encoder: Encoder, value: DependencyLatest) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.group)
                encodeStringElement(descriptor, 1, value.name)
                encodeStringElement(descriptor, 2, value.version)
                encodeNullableSerializableElement(descriptor, 3, String.serializer().nullable, value.projectUrl)
                encodeStringElement(descriptor, 4, value.latest)
            }
        }
    }

    @Serializer(forClass = DependencyUnresolved::class)
    object DependencyUnresolvedSerializer : KSerializer<DependencyUnresolved> {
        override val descriptor = SerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.DependencyUnresolved") {
            element("group", String.serializer().descriptor)
            element("name", String.serializer().descriptor)
            element("version", String.serializer().descriptor)
            element("projectUrl", String.serializer().nullable.descriptor)
            element("reason", String.serializer().descriptor)
        }

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
            var group: String? = null
            var name: String? = null
            var version: String? = null
            var projectUrl: String? = null
            var reason: String? = null

            loop@ while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    READ_DONE -> break@loop
                    0 -> group = decodeStringElement(descriptor, i)
                    1 -> name = decodeStringElement(descriptor, i)
                    2 -> version = decodeStringElement(descriptor, i)
                    3 -> projectUrl = decodeNullableSerializableElement(descriptor, i, String.serializer().nullable)
                    4 -> reason = decodeStringElement(descriptor, i)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            DependencyUnresolved(group, name, version, projectUrl, reason)
        }

        override fun serialize(encoder: Encoder, value: DependencyUnresolved) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.group)
                encodeStringElement(descriptor, 1, value.name)
                encodeStringElement(descriptor, 2, value.version)
                encodeNullableSerializableElement(descriptor, 3, String.serializer().nullable, value.projectUrl)
                encodeStringElement(descriptor, 4, value.reason)
            }
        }
    }

    @Serializer(forClass = VersionAvailable::class)
    object VersionAvailableSerializer : KSerializer<VersionAvailable> {
        override val descriptor = SerialDescriptor("com.github.benmanes.gradle.versions.reporter.result.VersionAvailable") {
            element("release", String.serializer().nullable.descriptor)
            element("milestone", String.serializer().nullable.descriptor)
            element("integration", String.serializer().nullable.descriptor)
        }

        override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
            var release: String? = null
            var milestone: String? = null
            var integration: String? = null

            loop@ while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    READ_DONE -> break@loop
                    0 -> release = decodeNullableSerializableElement(descriptor, i, String.serializer().nullable)
                    1 -> milestone = decodeNullableSerializableElement(descriptor, i, String.serializer().nullable)
                    2 -> integration = decodeNullableSerializableElement(descriptor, i, String.serializer().nullable)
                    else -> throw SerializationException("Unknown index $i")
                }
            }

            VersionAvailable(release, milestone, integration)
        }

        override fun serialize(encoder: Encoder, value: VersionAvailable) {
            encoder.encodeStructure(descriptor) {
                encodeNullableSerializableElement(descriptor, 0, String.serializer().nullable, value.release)
                encodeNullableSerializableElement(descriptor, 1, String.serializer().nullable, value.milestone)
                encodeNullableSerializableElement(descriptor, 2, String.serializer().nullable, value.integration)
            }
        }
    }
}
