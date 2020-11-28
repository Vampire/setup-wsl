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

import com.github.benmanes.gradle.versions.reporter.result.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration.Companion.Stable
import java.net.URLClassLoader
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.script.experimental.api.ResultValue.Value
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "1.3.72"
    id("com.github.ben-manes.versions") version "0.36.0"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")
        classpath("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
        classpath(kotlin("scripting-jvm-host", "1.3.72"))
    }
}

repositories {
    mavenCentral()
    jcenter()
    gradlePluginPortal()
}

val versions by lazy(NONE) {
    versionsByClassLoader ?: versionsByScripting
}

dependencies {
    implementation(gradlePlugin("com.github.ben-manes.versions"))
    implementation(gradlePlugin("net.researchgate.release"))
    implementation(gradlePlugin("net.wooga.github"))
    implementation(gradlePlugin("org.ajoberstar.grgit"))
    implementation(gradlePlugin("org.jetbrains.kotlin.js"))
    implementation("org.kohsuke:github-api".withVersion)
    implementation("com.charleskorn.kaml:kaml".withVersion)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime".withVersion)
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

tasks.dependencyUpdates {
    checkForGradleUpdate = false
    checkConstraints = true

    rejectVersionIf {
        val preliminaryReleaseRegex = Regex("""(?i)[.-](?:${
            listOf(
                    "alpha",
                    "beta",
                    "dev",
                    "rc",
                    "cr",
                    "m",
                    "preview",
                    "test",
                    "pr",
                    "pre",
                    "b",
                    "ea"
            ).joinToString("|")
        })[.\d-]*""")
        preliminaryReleaseRegex.containsMatchIn(candidate.version)
                && !preliminaryReleaseRegex.containsMatchIn(currentVersion)
    }

    outputFormatter = closureOf<Result> {
        gradle = null
        file("build/dependencyUpdates/report.json")
                .apply { parentFile.mkdirs() }
                .also { reportFile ->
                    Json(Stable.copy(prettyPrint = true))
                            .stringify(resultSerializer, this)
                            .also { reportFile.writeText(it) }
                }
    }
}

@Suppress("UNCHECKED_CAST")
val resultSerializerByClassLoader
    get() = layout
            .buildDirectory
            .dir("classes/kotlin/main")
            .get()
            .let { classesDir ->
                if (!classesDir.file("net/kautler/dao/ResultSerializer.class").asFile.isFile) {
                    return@let null
                }

                URLClassLoader(
                        arrayOf(classesDir.asFile.toURI().toURL()),
                        buildscript.classLoader
                )
                        .loadClass("net.kautler.dao.ResultSerializer")
                        .kotlin
                        .objectInstance
                        as KSerializer<Result>?
            }

@Suppress("UNCHECKED_CAST")
val resultSerializerByScripting
    get() = runBlocking {
        JvmScriptCompiler(defaultJvmScriptingHostConfiguration)(
                """
                    ${file("src/main/kotlin/net/kautler/dao/ResultSerializer.kt").readText()}
                    ResultSerializer
                """.toScriptSource(),
                ScriptCompilationConfiguration {
                    jvm {
                        dependenciesFromCurrentContext(
                                "gradle-versions-plugin",
                                "kotlinx-serialization-runtime",
                                "groovy-all"
                        )
                    }
                }
        ).onSuccess { BasicJvmScriptEvaluator()(it) }
    }
            .valueOrThrow()
            .returnValue
            .let { it as Value }
            .value
            as KSerializer<Result>

val resultSerializer by lazy(NONE) {
    resultSerializerByClassLoader ?: resultSerializerByScripting
}

@Suppress("UNCHECKED_CAST")
val versionsByClassLoader
    get() = layout
            .buildDirectory
            .dir("classes/kotlin/main")
            .get()
            .let { classesDir ->
                if (!classesDir.file("net/kautler/VersionsKt.class").asFile.isFile) {
                    return@let null
                }

                URLClassLoader(
                        arrayOf(classesDir.asFile.toURI().toURL()),
                        buildscript.classLoader
                )
                        .loadClass("net.kautler.VersionsKt")
                        .methods
                        .find { it.name == "getVersions" }
                        ?.invoke(null)
                        as Map<String, String>?
            }

@Suppress("UNCHECKED_CAST")
val versionsByScripting
    get() = runBlocking {
        JvmScriptCompiler(defaultJvmScriptingHostConfiguration)(
                """val versions = (mapOf\([^)]++\))"""
                        .toRegex()
                        .find(file("src/main/kotlin/net/kautler/Versions.kt").readText())!!
                        .groupValues[1]
                        .toScriptSource(),
                ScriptCompilationConfiguration()
        ).onSuccess { BasicJvmScriptEvaluator()(it) }
    }
            .valueOrThrow()
            .returnValue
            .let { it as Value }
            .value
            as Map<String, String>

val String.version get() = "${versions[this]}"

val String.withVersion get() = withVersion(split(":").last())

fun String.withVersion(key: String) = "$this:${key.version}"

fun gradlePlugin(plugin: String): String = plugin.let {
    val (id, version) = "$it:".split(":", limit = 3)
    "$id:$id.gradle.plugin:${version.ifBlank { id.version }}"
}
