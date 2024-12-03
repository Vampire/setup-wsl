/*
 * Copyright 2020-2024 Björn Kautler
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

import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY
import org.jetbrains.kotlin.cli.common.messages.MessageCollector.Companion.NONE
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles.JVM_CONFIG_FILES
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalVirtualFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import java.nio.file.Path

plugins {
    `java-base`
}

val compilerClasspath by configurations.creating {
    isCanBeConsumed = false
}

val scriptClasspath by configurations.creating {
    isCanBeConsumed = false
}

val libs = the<LibrariesForLibs>()

dependencies {
    compilerClasspath(libs.workflows.kotlin.compiler)
    compilerClasspath(libs.workflows.kotlin.scripting.compiler)
    scriptClasspath(libs.workflows.kotlin.main.kts) {
        isTransitive = false
    }
}

val preprocessWorkflows by tasks.registering {
    group = "github actions"
}
file(".github/workflows")
    .listFiles { _, name -> name.endsWith(".main.kts") }!!
    .forEach { workflowScript ->
        val workflowName = workflowScript.name.removeSuffix(".main.kts")
        val pascalCasedWorkflowName = workflowName.replace("""-\w""".toRegex()) {
            it.value.substring(1).replaceFirstChar(Char::uppercaseChar)
        }.replaceFirstChar(Char::uppercaseChar)
        val preprocessWorkflow = tasks.register<JavaExec>("preprocess${pascalCasedWorkflowName}Workflow") {
            group = "github actions"

            inputs
                .file(workflowScript)
                .withPropertyName("workflowScript")
            inputs
                .files(file(workflowScript).importedFiles)
                .withPropertyName("importedFiles")
            outputs
                .file(workflowScript.resolveSibling("$workflowName.yaml"))
                .withPropertyName("workflowFile")

            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(17))
            })
            classpath(compilerClasspath)
            mainClass.set(K2JVMCompiler::class.qualifiedName)
            args("-no-stdlib", "-no-reflect")
            args("-classpath", scriptClasspath.asPath)
            args("-script", workflowScript.absolutePath)

            // work-around for https://youtrack.jetbrains.com/issue/KT-42101
            systemProperty("kotlin.main.kts.compiled.scripts.cache.dir", "")
        }
        preprocessWorkflows {
            dependsOn(preprocessWorkflow)
        }
    }

val File.importedFiles: List<File>
    get() = if (!isFile) {
        emptyList()
    } else {
        PsiManager
            .getInstance(
                KotlinCoreEnvironment
                    .createForProduction(
                        Disposer.newDisposable(),
                        CompilerConfiguration().apply {
                            put(MESSAGE_COLLECTOR_KEY, NONE)
                        },
                        JVM_CONFIG_FILES
                    )
                    .project
            )
            .findFile(
                // work-around for API change between version we compile against and version we run against
                // after upgrading Gradle to a version that contains Kotlin 1.9 the embeddable compiler can
                // be upgraded to v2 also for compilation and then this can be removed
                CoreLocalVirtualFile::class
                    .java
                    .getConstructor(CoreLocalFileSystem::class.java, Path::class.java)
                    .newInstance(CoreLocalFileSystem(), toPath())
            )
            .let { it as KtFile }
            .fileAnnotationList
            ?.annotationEntries
            ?.asSequence()
            ?.filter { it.shortName?.asString() == "Import" }
            ?.flatMap { it.valueArgumentList?.arguments ?: emptyList() }
            ?.mapNotNull { it.getArgumentExpression() as? KtStringTemplateExpression }
            ?.map { it.entries.first() }
            ?.mapNotNull { it as? KtLiteralStringTemplateEntry }
            ?.map { resolveSibling(it.text) }
            ?.flatMap { it.importedFiles + it }
            ?.toList()
            ?: emptyList()
    }
