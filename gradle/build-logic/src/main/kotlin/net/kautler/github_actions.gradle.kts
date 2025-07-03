/*
 * Copyright 2020-2025 Björn Kautler
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

import net.kautler.githubactions.DetermineImportedFiles
import net.kautler.githubactions.PreprocessGithubWorkflow
import org.ajoberstar.grgit.operation.BranchListOp.Mode.ALL
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-base`
    id("org.ajoberstar.grgit.service")
}

val compilerEmbeddableClasspath by configurations.creating {
    isCanBeConsumed = false
}

val compilerClasspath by configurations.creating {
    isCanBeConsumed = false
}

val scriptClasspath by configurations.creating {
    isCanBeConsumed = false
}

val libs = the<LibrariesForLibs>()

dependencies {
    compilerEmbeddableClasspath(libs.workflows.kotlin.compiler.embeddable)
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
        val determineImportedFiles =
            tasks.register<DetermineImportedFiles>("determineImportedFilesFor${pascalCasedWorkflowName}Workflow") {
                mainKtsFile.set(workflowScript)
                importedFiles.set(layout.buildDirectory.file("importedFilesFor${pascalCasedWorkflowName}Workflow.txt"))
                kotlinCompilerEmbeddableClasspath.from(compilerEmbeddableClasspath)
            }
        val preprocessWorkflow =
            tasks.register<PreprocessGithubWorkflow>("preprocess${pascalCasedWorkflowName}Workflow") {
                this.workflowScript.set(workflowScript)
                importedFiles.from(determineImportedFiles.flatMap { it.importedFiles }.map { it.asFile.readLines() })
                kotlinCompilerClasspath.from(compilerClasspath)
                mainKtsClasspath.from(scriptClasspath)
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(17))
                })
            }
        val deleteWorkflowYaml = tasks.register<Delete>("delete${pascalCasedWorkflowName}WorkflowYaml") {
            delete(preprocessWorkflow.flatMap { it.workflowFile })
        }
        preprocessWorkflow {
            dependsOn(deleteWorkflowYaml)
        }
        preprocessWorkflows {
            dependsOn(preprocessWorkflow)
        }
    }

val majorVersion: String by project
val preprocessVerifyReleaseWorkflow by tasks.existing(PreprocessGithubWorkflow::class) {
    inputs.property("majorVersion", majorVersion)

    doLast {
        val grgit = grgitService
            .service
            .get()
            .grgit

        if (grgit
                .branch
                .list { mode = ALL }
                .asSequence()
                .map { it.name }
                .contains("origin/v$majorVersion")
        ) {
            workflowFile.get().apply {
                readText()
                    .replace(
                        """'Vampire/setup-wsl@v(?<version>\d++)'""".toRegex(),
                        {
                            if (it.groups["version"]!!.value.toInt() < majorVersion.toInt()) {
                                "'Vampire/setup-wsl@v$majorVersion'"
                            } else it.value
                        }
                    )
                    .also { writeText(it) }
            }
        }
    }
}
