/*
 * Copyright 2025 Björn Kautler
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

package net.kautler.githubactions

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY
import org.jetbrains.kotlin.cli.common.messages.MessageCollector.Companion.NONE
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
import java.io.File
import java.nio.file.Path

abstract class DetermineImportedFilesWorkAction : WorkAction<DetermineImportedFilesWorkAction.Parameters> {
    override fun execute() {
        val projectDirectory = parameters.projectDirectory.get().asFile
        parameters
            .mainKtsFile
            .get()
            .asFile
            .importedFiles
            .map { it.relativeTo(projectDirectory).invariantSeparatorsPath }
            .distinct()
            .sorted()
            .joinToString("\n")
            .also(parameters.importedFiles.get().asFile::writeText)
    }

    interface Parameters : WorkParameters {
        val projectDirectory: DirectoryProperty
        val mainKtsFile: RegularFileProperty
        val importedFiles: RegularFileProperty
    }
}

private val File.importedFiles: List<File>
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
            .findFile(CoreLocalVirtualFile::class.java.getConstructor(CoreLocalFileSystem::class.java, Path::class.java).newInstance(CoreLocalFileSystem(), toPath()))
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
