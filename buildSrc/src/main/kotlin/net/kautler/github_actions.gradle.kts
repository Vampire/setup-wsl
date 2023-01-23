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
        val preProcessWorkflow = tasks.register<JavaExec>("preProcess${camelCasedWorkflowName}Workflow") {
            inputs.file(workflowScript)
            outputs.file(workflowScript.resolveSibling("$workflowName.yaml"))
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
            dependsOn(preProcessWorkflow)
        }
    }
