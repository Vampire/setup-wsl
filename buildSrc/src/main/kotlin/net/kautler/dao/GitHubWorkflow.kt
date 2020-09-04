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
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
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
import kotlinx.serialization.builtins.list
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.set
import kotlinx.serialization.decodeStructure
import net.kautler.dao.GitHubWorkflow.OnDetails.OnMapSerializer
import net.kautler.util.get
import net.kautler.util.getKey
import kotlin.reflect.full.memberProperties

@Serializable
data class GitHubWorkflow(
        val name: String? = null,
        @Serializable(with = OnMapSerializer::class)
        val on: Map<String, OnDetails?>,
        val env: Map<String, String>? = null,
        val defaults: Defaults? = null,
        val jobs: MutableMap<String, Job>
) {
    sealed class OnDetails {
        interface OnTypedEventDetails {
            val types: List<String>?
        }

        interface OnBranchEventDetails {
            val branches: List<String>?

            @SerialName("branches-ignore")
            val branchesIgnore: List<String>?
        }

        @Serializable
        data class OnEventDetails(
                override val types: List<String>? = null
        ) : OnDetails(), OnTypedEventDetails

        @Serializable
        data class OnPushOrPullRequestEventDetails(
                override val types: List<String>? = null,
                override val branches: List<String>? = null,
                override val branchesIgnore: List<String>? = null,
                val tags: List<String>? = null,
                @SerialName("tags-ignore")
                val tagsIgnore: List<String>? = null,
                val paths: List<String>? = null,
                @SerialName("paths-ignore")
                val pathsIgnore: List<String>? = null
        ) : OnDetails(), OnTypedEventDetails, OnBranchEventDetails

        @Serializable
        data class OnWorkflowRunEventDetails(
                override val types: List<String>? = null,
                val workflows: List<String>? = null,
                override val branches: List<String>? = null,
                override val branchesIgnore: List<String>? = null
        ) : OnDetails(), OnTypedEventDetails, OnBranchEventDetails

        @Serializable
        data class OnScheduleDetails(
                val schedules: List<Schedule>
        ) : OnDetails()

        @Serializable
        data class Schedule(
                val cron: String
        )

        @Serializer(forClass = OnDetails::class)
        companion object : KSerializer<OnDetails?> {
            override val descriptor =
                    SerialDescriptor("net.kautler.dao.GitHubWorkflow.OnDetails", CONTEXTUAL)

            override fun deserialize(decoder: Decoder): OnDetails? {
                check(decoder is YamlInput) { "This class can only be loaded using kaml" }

                val context = decoder.node
                check(context is YamlMap) { "Expected a YamlMap as current context" }

                return decoder.decodeStructure(descriptor) {
                    this as YamlInput
                    when (context.getKey(node)) {
                        "push", "pull_request" -> decodeSerializableValue(OnPushOrPullRequestEventDetails.serializer())
                        "workflow_run" -> decodeSerializableValue(OnWorkflowRunEventDetails.serializer())
                        "schedule" -> OnScheduleDetails(decodeSerializableValue(Schedule.serializer().list))
                        else -> decodeSerializableValue(OnEventDetails.serializer())
                    }
                }
            }

            override fun serialize(encoder: Encoder, value: OnDetails?) {
                when (value) {
                    is OnPushOrPullRequestEventDetails ->
                        encoder.encodeSerializableValue(OnPushOrPullRequestEventDetails.serializer(), value)

                    is OnWorkflowRunEventDetails ->
                        encoder.encodeSerializableValue(OnWorkflowRunEventDetails.serializer(), value)

                    is OnScheduleDetails ->
                        encoder.encodeSerializableValue(Schedule.serializer().list, value.schedules)

                    is OnEventDetails ->
                        encoder.encodeSerializableValue(OnEventDetails.serializer(), value)
                }
            }
        }

        @Serializer(forClass = Map::class)
        class OnMapSerializer(
                private val keySerializer: KSerializer<String>,
                private val valueSerializer: KSerializer<OnDetails?>
        ) : KSerializer<Map<String, OnDetails?>> {
            override val descriptor =
                    SerialDescriptor("net.kautler.dao.GitHubWorkflow.on", CONTEXTUAL)

            override fun deserialize(decoder: Decoder): Map<String, OnDetails?> {
                check(decoder is YamlInput) { "This class can only be loaded using kaml" }

                val context = decoder.node
                check(context is YamlMap) { "Expected a YamlMap as current context" }

                return when (val onValueNode: YamlNode = context["on"]!!) {
                    is YamlScalar -> decoder.decodeStructure(String.serializer().descriptor) {
                        this as YamlInput
                        mapOf(decodeString() to null)
                    }

                    is YamlList -> decoder.decodeStructure(String.serializer().list.descriptor) {
                        check(onValueNode.items.all { it is YamlScalar }) { "Invalid on value list content" }
                        this as YamlInput
                        decodeSerializableValue(String.serializer().list)
                                .map { it to null }
                                .toMap()
                    }

                    is YamlMap -> decoder.decodeSerializableValue(MapSerializer(keySerializer, valueSerializer))

                    else -> error("Unexpected type for on value: ${onValueNode::class}")
                }
            }

            override fun serialize(encoder: Encoder, value: Map<String, OnDetails?>) {
                when {
                    (value.size == 1) && value.values.all { it == null } ->
                        encoder.encodeString(value.entries.first().key)

                    (value.size > 1) && value.values.all { it == null } ->
                        encoder.encodeSerializableValue(String.serializer().set, value.keys)

                    else -> encoder.encodeSerializableValue(MapSerializer(keySerializer, valueSerializer), value)
                }
            }
        }
    }

    @Serializable
    data class Defaults(
            val run: RunDefaults? = null
    )

    @Serializable
    data class RunDefaults(
            val shell: String? = null,
            @SerialName("working-directory")
            val workingDirectory: String? = null
    )

    @Serializable
    data class Job(
            val name: String? = null,
            @Serializable(with = NeedsSerializer::class)
            val needs: List<String>? = null,
            @Serializable(with = RunsOnSerializer::class)
            @SerialName("runs-on")
            val runsOn: List<String>,
            val outputs: Map<String, String>? = null,
            val env: Map<String, String>? = null,
            val defaults: Defaults? = null,
            @SerialName("if")
            val ifCondition: String? = null,
            val steps: List<Step>,
            @SerialName("timeout-minutes")
            val timeoutMinutes: Long? = null,
            val strategy: Strategy? = null,
            @SerialName("continue-on-error")
            val continueOnError: Boolean? = null,
            @Serializable(with = ContainerSerializer::class)
            val container: Container? = null,
            val services: Map<String, Container>? = null
    ) {
        @Serializer(forClass = List::class)
        class NeedsSerializer(private val elementSerializer: KSerializer<String>) : KSerializer<List<String>> {
            override val descriptor =
                    SerialDescriptor("net.kautler.dao.GitHubWorkflow.Job.needs", CONTEXTUAL)

            override fun deserialize(decoder: Decoder): List<String> {
                check(decoder is YamlInput) { "This class can only be loaded using kaml" }

                val context = decoder.node
                check(context is YamlMap) { "Expected a YamlMap as current context" }

                return when (val needsValueNode: YamlNode = context["needs"]!!) {
                    is YamlScalar -> decoder.decodeStructure(elementSerializer.descriptor) {
                        this as YamlInput
                        listOf(decodeString())
                    }

                    is YamlList -> decoder.decodeStructure(elementSerializer.list.descriptor) {
                        check(needsValueNode.items.all { it is YamlScalar }) { "Invalid needs value list content" }
                        this as YamlInput
                        decodeSerializableValue(elementSerializer.list)
                    }

                    else -> error("Unexpected type for needs value: ${needsValueNode::class}")
                }
            }

            override fun serialize(encoder: Encoder, value: List<String>) {
                if (value.size == 1) {
                    encoder.encodeString(value.first())
                } else {
                    encoder.encodeSerializableValue(elementSerializer.list, value)
                }
            }
        }

        @Serializer(forClass = List::class)
        class RunsOnSerializer(private val elementSerializer: KSerializer<String>) : KSerializer<List<String>> {
            override val descriptor =
                    SerialDescriptor("net.kautler.dao.GitHubWorkflow.Job.runsOn", CONTEXTUAL)

            override fun deserialize(decoder: Decoder): List<String> {
                check(decoder is YamlInput) { "This class can only be loaded using kaml" }

                val context = decoder.node
                check(context is YamlMap) { "Expected a YamlMap as current context" }

                return when (val runsOnValueNode: YamlNode = context["runs-on"]!!) {
                    is YamlScalar -> decoder.decodeStructure(elementSerializer.descriptor) {
                        this as YamlInput
                        listOf(decodeString())
                    }

                    is YamlList -> decoder.decodeStructure(elementSerializer.list.descriptor) {
                        check(runsOnValueNode.items.all { it is YamlScalar }) { "Invalid runs-on value list content" }
                        this as YamlInput
                        decodeSerializableValue(elementSerializer.list)
                    }

                    else -> error("Unexpected type for runs-on value: ${runsOnValueNode::class}")
                }
            }

            override fun serialize(encoder: Encoder, value: List<String>) {
                if (value.size == 1) {
                    encoder.encodeString(value.first())
                } else {
                    encoder.encodeSerializableValue(elementSerializer.list, value)
                }
            }
        }

        @Serializer(forClass = Container::class)
        object ContainerSerializer : KSerializer<Container> {
            override val descriptor =
                    SerialDescriptor("net.kautler.dao.GitHubWorkflow.Job.container", CONTEXTUAL)

            override fun deserialize(decoder: Decoder): Container {
                check(decoder is YamlInput) { "This class can only be loaded using kaml" }

                val context = decoder.node
                check(context is YamlMap) { "Expected a YamlMap as current context" }

                return when (val containerValueNode: YamlNode = context["container"]!!) {
                    is YamlScalar -> decoder.decodeStructure(String.serializer().descriptor) {
                        this as YamlInput
                        Container(decodeString())
                    }

                    is YamlMap -> decoder.decodeSerializableValue(Container.serializer())

                    else -> error("Unexpected type for container value: ${containerValueNode::class}")
                }
            }

            override fun serialize(encoder: Encoder, value: Container) {
                if (Container::class.memberProperties.filter { it.name != "image" }.all { it.get(value) == null }) {
                    encoder.encodeString(value.image)
                } else {
                    encoder.encodeSerializableValue(Container.serializer(), value)
                }
            }
        }
    }

    @Serializable
    data class Step(
            val id: String? = null,
            @SerialName("if")
            val ifCondition: String? = null,
            val name: String? = null,
            val uses: String? = null,
            val run: String? = null,
            val shell: String? = null,
            @SerialName("working-directory")
            val workingDirectory: String? = null,
            val with: Map<String, String>? = null,
            val env: Map<String, String>? = null,
            @SerialName("continue-on-error")
            val continueOnError: Boolean? = null,
            @SerialName("timeout-minutes")
            val timeoutMinutes: Long? = null
    )

    @Serializable
    data class Strategy(
            val matrix: Map<String, MatrixValue>? = null,
            @SerialName("fail-fast")
            val failFast: Boolean? = null,
            @SerialName("max-parallel")
            val maxParallel: Long? = null
    ) {
        sealed class MatrixValue {
            class MatrixVariable(
                    val value: List<MatrixLeaf>
            ) : MatrixValue()

            class MatrixIncludeOrExclude(
                    val value: List<Map<String, MatrixLeaf>>
            ) : MatrixValue()

            @Serializer(forClass = MatrixValue::class)
            companion object : KSerializer<MatrixValue> {
                override val descriptor =
                        SerialDescriptor("net.kautler.dao.GitHubWorkflow.MatrixValue", CONTEXTUAL)

                override fun deserialize(decoder: Decoder): MatrixValue {
                    check(decoder is YamlInput) { "This class can only be loaded using kaml" }

                    val context = decoder.node
                    check(context is YamlMap) { "Expected a YamlMap as current context" }

                    return decoder.decodeStructure(descriptor) {
                        this as YamlInput
                        when (context.getKey(node)) {
                            "include", "exclude" -> MatrixIncludeOrExclude(decodeSerializableValue(
                                    MapSerializer(String.serializer(), MatrixLeaf).list)
                            )
                            else -> MatrixVariable(decodeSerializableValue(MatrixLeaf.list))
                        }
                    }
                }

                override fun serialize(encoder: Encoder, value: MatrixValue) {
                    when (value) {
                        is MatrixVariable -> encoder.encodeSerializableValue(
                                MatrixLeaf.list,
                                value.value
                        )

                        is MatrixIncludeOrExclude -> encoder.encodeSerializableValue(
                                MapSerializer(String.serializer(), MatrixLeaf).list,
                                value.value
                        )
                    }
                }
            }
        }

        sealed class MatrixLeaf {
            class MatrixMapLeaf(
                    val value: Map<String, MatrixLeaf?>
            ) : MatrixLeaf()

            class MatrixStringLeaf(
                    val value: String
            ) : MatrixLeaf()

            @Serializer(forClass = MatrixLeaf::class)
            companion object : KSerializer<MatrixLeaf> {
                override val descriptor =
                        SerialDescriptor("net.kautler.dao.GitHubWorkflow.MatrixValue", CONTEXTUAL)

                override fun deserialize(decoder: Decoder): MatrixLeaf {
                    check(decoder is YamlInput) { "This class can only be loaded using kaml" }

                    return decoder.decodeStructure(descriptor) {
                        this as YamlInput
                        when (node) {
                            is YamlScalar -> decodeStructure(String.serializer().descriptor) {
                                this as YamlInput
                                MatrixStringLeaf(decodeString())
                            }

                            is YamlMap -> MatrixMapLeaf(decodeSerializableValue(
                                    MapSerializer(String.serializer(), MatrixLeaf.nullable)
                            ))

                            else -> error("Unexpected type for context node: ${node::class}")
                        }
                    }
                }

                override fun serialize(encoder: Encoder, value: MatrixLeaf) {
                    when (value) {
                        is MatrixMapLeaf -> encoder.encodeSerializableValue(
                                MapSerializer(String.serializer(), MatrixLeaf.nullable),
                                value.value
                        )

                        is MatrixStringLeaf -> encoder.encodeSerializableValue(
                                String.serializer(),
                                value.value
                        )
                    }
                }
            }
        }
    }

    @Serializable
    data class Container(
            val image: String,
            val env: Map<String, String>? = null,
            val ports: List<String>? = null,
            val volumes: List<String>? = null,
            val options: String? = null
    )
}
