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
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@UntrackedTask(because = "imported files can import other files so inputs are not determinable upfront")
abstract class DetermineImportedFiles : DefaultTask() {
    @get:InputFile
    abstract val mainKtsFile: RegularFileProperty

    @get:InputFiles
    abstract val kotlinCompilerEmbeddableClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val importedFiles: RegularFileProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @get:Inject
    abstract val layout: ProjectLayout

    @TaskAction
    fun determineImportedFiles() {
        workerExecutor.classLoaderIsolation {
            classpath.from(kotlinCompilerEmbeddableClasspath)
        }.submit(DetermineImportedFilesWorkAction::class) {
            projectDirectory.set(layout.projectDirectory)
            mainKtsFile.set(this@DetermineImportedFiles.mainKtsFile)
            importedFiles.set(this@DetermineImportedFiles.importedFiles)
        }
    }
}
