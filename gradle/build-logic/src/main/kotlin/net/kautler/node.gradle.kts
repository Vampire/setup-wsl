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

package net.kautler

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.ExperimentalSerializationApi
import net.kautler.dao.action.GitHubAction
import net.kautler.util.npm
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.targets.js.dukat.IntegratedDukatTask
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import java.security.MessageDigest
import kotlin.text.RegexOption.MULTILINE

plugins {
    kotlin("js")
}

kotlin {
    js(IR) {
        useCommonJs()
        binaries.executable()
        nodejs()
    }
}

// work-around for https://youtrack.jetbrains.com/issue/KT-56305
tasks.withType<Copy>().configureEach {
    if (name.endsWith("ExecutableCompileSync")) {
        doFirst {
            outputs.files.forEach { it.deleteRecursively() }
        }
    }
}

@ExperimentalSerializationApi
val inputDefaultValues by lazy {
    Yaml
        .default
        .decodeFromString(GitHubAction.serializer(), file("action.yml").readText())
        .inputs
        ?.filterValues { it.default != null }
        ?.filterKeys { !System.getenv().containsKey("INPUT_${it.uppercase()}") }
}

// work-around for https://youtrack.jetbrains.com/issue/KT-56305
tasks.withType<NodeJsExec>().configureEach {
    val toolCacheDir = "$temporaryDir/tool-cache"

    environment("RUNNER_TEMP", "$temporaryDir/runner-temp")
    environment("RUNNER_TOOL_CACHE", toolCacheDir)

    @OptIn(ExperimentalSerializationApi::class)
    inputDefaultValues?.forEach { (name, input) ->
        environment(
            "INPUT_${name.uppercase()}",
            if (name == "use-cache") "false" else input.default!!
        )
    }

    doFirst("Delete tool-cache") {
        file(toolCacheDir).deleteRecursively()
    }
}

val libs = the<LibrariesForLibs>()

configure<NodeJsRootExtension> {
    nodeVersion = libs.versions.build.node.get()
    versions.dukat.version = libs.versions.build.dukat.get()

    // work-around for https://github.com/Kotlin/dukat/issues/103
    npmInstallTaskProvider!!.configure {
        val patchedDukat0012CliJs = layout
            .projectDirectory
            .file("resources/dukat-cli-0.0.12.js")

        val patchedDukat057CliJs = layout
            .projectDirectory
            .file("resources/dukat-cli-0.5.7.js")

        inputs
            .files(patchedDukat0012CliJs, patchedDukat057CliJs)
            .withPropertyName("patched dukat-cli.js files")

        doLast {
            val dukatCliJs = rootPackageDir.resolve("node_modules/dukat/bin/dukat-cli.js")

            if (dukatCliJs.exists()) {
                val sha256 = MessageDigest
                    .getInstance("SHA-256")
                    .digest(dukatCliJs.readBytes())
                    .joinToString("") { "%02x".format(it) }

                when (sha256) {
                    // already a patched version
                    "32b7c91ecf8c94b8f1e554da870a617437e421db07e40d558dbb959bd221cb5e",
                    "e16d684be9b90c2d5d78f90077dc9690253fd4d7bc6b48f94bbd48b1692bf2d5" ->
                        return@doLast

                    // original 0.0.12 version
                    "ceebdfbb94d103eb44da265b572e51fb69ff4bb1c524b94f621c6a6e94716ac8" ->
                        patchedDukat0012CliJs.asFile.copyTo(dukatCliJs, overwrite = true)

                    // original 0.5.7 version
                    "12645e1491b68638d2c1c4146502ae9ed5037baa82cc12e6c55070471d962187" ->
                        patchedDukat057CliJs.asFile.copyTo(dukatCliJs, overwrite = true)

                    else -> throw RuntimeException("dukat-cli.js has unexpected checksum $sha256")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.extensions)
    implementation(libs.kotlinx.nodejs)
    implementation(npm(libs.actions.cache))
    implementation(npm(libs.actions.core))
    implementation(npm(libs.actions.exec))
    implementation(npm(libs.actions.http.client))
    implementation(npm(libs.actions.io))
    implementation(npm(libs.actions.tool.cache))
    implementation(npm(libs.types.semver))
    implementation(npm(libs.semver, generateExternals = false))
    implementation(npm(libs.nullWritable))
}

tasks.withType(IntegratedDukatTask::class).configureEach {
    doLast {
        // work-around for https://github.com/Kotlin/dukat/issues/240
        addJsModuleAnnotations(
            this,
            "core.module_@actions_core.kt" to "@actions/core",
            "io.module_@actions_io.kt" to "@actions/io",
            "exec.module_@actions_exec.kt" to "@actions/exec",
            "index.module_@actions_http-client.kt" to "@actions/http-client",
            "tool-cache.module_@actions_tool-cache.kt" to "@actions/tool-cache",
            "cache.module_@actions_cache.kt" to "@actions/cache"
        )
        // work-around for https://github.com/Kotlin/dukat/issues/397
        deleteExternalsFiles(
            this,
            "*.module_node.kt",
            "*.nonDeclarations.kt"
        )
        fixExternalsFiles(
            this,
            "lib.dom.kt" to listOf(
                """\Qoverride fun addEventListener(type: String, listener: EventListenerObject\E""" to """fun addEventListener(type: String, listener: EventListenerObject""",
                """\Qoverride fun removeEventListener(type: String, callback: EventListenerObject\E""" to """fun removeEventListener(type: String, callback: EventListenerObject""",
                """\Q`T${'$'}16`\E""" to """`T\${'$'}16`<R, T>"""
            ),
            // work-around for https://github.com/Kotlin/dukat/issues/402
            "lib.es2018.asynciterable.module_dukat.kt" to listOf(
                """\Qval `return`: ((value: TReturn) -> Promise<dynamic /* IteratorYieldResult<T> | IteratorReturnResult<TReturn> */>)?\E$""" to "val `return`: ((value: dynamic) -> Promise<dynamic /* IteratorYieldResult<T> | IteratorReturnResult<TReturn> */>)?",
                """^*\Qval `return`: ((value: PromiseLike<TReturn>) -> Promise<dynamic /* IteratorYieldResult<T> | IteratorReturnResult<TReturn> */>)?\E\r?\n\Q        get() = definedExternally\E\r?\n""" to ""
            ),
            // work-around for https://github.com/Kotlin/dukat/issues/401
            "null-writable.module_null-writable.kt" to listOf(
                """\Q`T$13`\E""" to """`T\$10`"""
            ),
            // work-around for https://github.com/Kotlin/dukat/issues/399
            "tool-cache.module_@actions_tool-cache.kt" to listOf(
                """\Qtypealias HTTPError = Error\E$""" to "external class HTTPError : Throwable",
                """\Qtypealias IToolRelease = IToolRelease\E$""" to "",
                """\Qtypealias IToolReleaseFile = IToolReleaseFile\E$""" to ""
            ),
            // work-around for https://github.com/Kotlin/dukat/issues/398
            "cache.module_@actions_cache.kt" to listOf(
                """\Qtypealias ValidationError = Error\E$""" to "external class ValidationError : Throwable",
                """\Qtypealias ReserveCacheError = Error\E$""" to "external class ReserveCacheError : Throwable"
            ),
            "index.module_@actions_http-client.kt" to listOf(
                """\Qtypealias HttpClientError = Error\E$""" to "external class HttpClientError : Throwable"
            ),
            "interfaces.module_@actions_http-client.kt" to listOf(
                """\Qexternal interface HttpClient {\E$""" to "external interface HttpClient2 {"
            ),
            // work-around for https://github.com/Kotlin/dukat/issues/400
            "semver.module_semver.kt" to listOf(
                """\Q@JsModule("semver")\E$""" to """@JsModule("semver/classes/semver")"""
            ),
            "interfaces.module_@actions_exec.kt" to listOf(
                """\Qimport buffer.global.Buffer\E$""" to """import Buffer"""
            ),
            "lib.es5.kt" to listOf(
                """\Qval resolve: ((specified: String, parent: URL) -> Promise<String>)?\E$""" to """val resolve2: ((specified: String, parent: URL) -> Promise<String>)?"""
            ),
            "lib.es2020.bigint.module_dukat.kt" to listOf(
                """\Q : RelativeIndexable<Any>\E""" to """"""
            )
        )
    }
}

tasks.assemble {
    dependsOn(project(":ncc-packer").tasks.named("nodeProductionRun"))
}

fun addJsModuleAnnotations(task: Task, vararg pairs: Pair<String, String>) {
    for ((file, module) in pairs) {
        task
            .outputs
            .files
            .asFileTree
            .matching { include("**/$file") }
            .singleFile
            .apply {
                writeText(
                    """
                        |@file:JsModule("$module")
                        ${readText().prependIndent("|")}
                    """.trimMargin()
                )
            }
    }
}

fun deleteExternalsFiles(task: Task, vararg files: String) {
    task
        .outputs
        .files
        .asFileTree
        .matching {
            for (file in files) {
                include("**/$file")
            }
        }
        .forEach { it.delete() }
}

fun fixExternalsFiles(task: Task, vararg pairs: Pair<String, List<Pair<String, String>>>) {
    for ((file, fixups) in pairs) {
        task
            .outputs
            .files
            .asFileTree
            .matching { include("**/$file") }
            .singleFile
            .apply {
                writeText(
                    fixups
                        .map { (pattern, replacement) -> pattern.toRegex(MULTILINE) to replacement }
                        .fold(readText()) { current, (regex, replacement) ->
                            regex.replace(current, replacement)
                        }
                )
            }
    }
}

fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}
