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

import groovy.lang.Closure
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
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

plugins {
    `java-base`
}

val compilerClasspath by configurations.creating {
    isCanBeConsumed = false
}

val scriptClasspath by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    compilerClasspath(kotlin("compiler", "1.8.0"))
    compilerClasspath(kotlin("scripting-compiler", "1.8.0"))
    add(scriptClasspath.name, kotlin("main-kts", "1.8.0"), closureOf<ExternalModuleDependency> {
        isTransitive = false
    } as Closure<Any>)
}

val preprocessWorkflows by tasks.registering
file(".github/workflows")
    .listFiles { _, name -> name.endsWith(".main.kts") }!!
    .forEach { workflowScript ->
        val workflowName = workflowScript.name.removeSuffix(".main.kts")
        val camelCasedWorkflowName = workflowName.replace("""-\w""".toRegex()) {
            it.value.substring(1).capitalize()
        }.capitalize()
        val preprocessWorkflow = tasks.register<JavaExec>("preprocess${camelCasedWorkflowName}Workflow") {
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
            mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            args("-no-stdlib", "-no-reflect")
            args("-classpath", scriptClasspath.asPath)
            args("-script", workflowScript.absolutePath)
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
                            put(MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                        },
                        JVM_CONFIG_FILES
                    )
                    .project
            )
            .findFile(
                CoreLocalVirtualFile(
                    CoreLocalFileSystem(),
                    this
                )
            )
            .let { it as KtFile }
            .fileAnnotationList
            ?.annotationEntries
            ?.filter { it.shortName?.asString() == "Import" }
            ?.flatMap { it.valueArgumentList?.arguments ?: emptyList() }
            ?.mapNotNull { it.getArgumentExpression() as? KtStringTemplateExpression }
            ?.map { it.entries.first() }
            ?.mapNotNull { it as? KtLiteralStringTemplateEntry }
            ?.map { resolveSibling(it.text) }
            ?.flatMap { it.importedFiles + it }
            ?: emptyList()
    }
