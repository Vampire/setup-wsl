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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

abstract class PreprocessGithubWorkflow : DefaultTask() {
    @get:InputFile
    abstract val workflowScript: RegularFileProperty

    @get:InputFiles
    abstract val importedFiles: ConfigurableFileCollection

    @get:InputFiles
    abstract val kotlinCompilerClasspath: ConfigurableFileCollection

    @get:InputFiles
    abstract val mainKtsClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:OutputFile
    val workflowFile: Provider<File> = workflowScript.map {
        val workflowScript = it.asFile
        workflowScript.resolveSibling("${workflowScript.name.removeSuffix(".main.kts")}.yaml")
    }

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    init {
        group = "github workflows"
    }

    @TaskAction
    fun determineImportedFiles() {
        workerExecutor.noIsolation().submit(PreprocessGithubWorkflowWorkAction::class) {
            workflowScript.set(this@PreprocessGithubWorkflow.workflowScript)
            kotlinCompilerClasspath.from(this@PreprocessGithubWorkflow.kotlinCompilerClasspath)
            mainKtsClasspath.from(this@PreprocessGithubWorkflow.mainKtsClasspath)
            javaExecutable.set(this@PreprocessGithubWorkflow.javaLauncher.map { it.executablePath })
        }
    }
}
