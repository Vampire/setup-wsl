/*
 * Copyright 2020-2026 Björn Kautler
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

import net.kautler.util.ProblemsProvider
import org.gradle.kotlin.dsl.newInstance
import java.security.MessageDigest

plugins {
    // needed for accessing majorVersion
    id("net.kautler.dependencies")
    `lifecycle-base`
}

val majorVersion: String by project
val readmeTemplateFilePath = "readme/README_template.md"
val readmeFilePath = "README.md"
val readmeChecksumFilePath = "readme/$readmeFilePath.sha256"

val verifyReadme by tasks.registering(ReadmeTask::class) {
    val majorVersion = majorVersion
    inputs.property("majorVersion", majorVersion)
    val readmeTemplateFile = file(readmeTemplateFilePath)
    inputs.file(readmeTemplateFile).withPropertyName("readmeTemplate")
    val readmeFile = file(readmeFilePath)
    inputs.files(readmeFile).withPropertyName("readme")
    val readmeChecksumFile = file(readmeChecksumFilePath)
    inputs.file(readmeChecksumFile).withPropertyName("readmeChecksum")

    val allTasks = provider { gradle.taskGraph.allTasks.map { it.path } }
    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    doLast("verify readme") {
        if (!readmeFile.exists() || (readmeChecksumFile.readText() != calculateChecksum(readmeFile))) {
            throw problemReporter.throwing(
                IllegalStateException(),
                ProblemId.create(
                    "the-${readmeFile.path}-file-was-tampered-with-manually",
                    buildString {
                        append("The ${readmeFile.path} file was tampered with manually")
                        // do not use hasTask() as this requires realization of the task that maybe is not necessary
                        if (allTasks.get().contains(":updateReadme")) {
                            append(""", if you want to overwrite it, add "-x $name" to your Gradle call""")
                        }
                    },
                    ProblemGroup.create("readme-tampering", "README Tampering")
                )
            ) {
                solution("Modify the ${readmeTemplateFile.path} file instead")
                solution("Revert the tampered ${readmeFile.path} file")
                solution("Overwrite the tampered ${readmeFile.path} file using the 'updateReadme' task")
                severity(Severity.ERROR)
            }
        }

        fs.copy {
            from(readmeTemplateFile)
            rename { readmeFile.name }
            filteringCharset = "UTF-8"
            expand("majorVersion" to majorVersion)
            into(temporaryDir)
        }
    }
}

tasks.check {
    dependsOn(verifyReadme)
}

val updateReadme by tasks.registering(ReadmeTask::class) {
    dependsOn(verifyReadme)

    val majorVersion = majorVersion
    inputs.property("majorVersion", majorVersion)
    val readmeTemplateFile = file(readmeTemplateFilePath)
    inputs.file(readmeTemplateFile).withPropertyName("readmeTemplate")
    val readmeFile = file(readmeFilePath)
    outputs.file(readmeFile).withPropertyName("readme")
    val readmeChecksumFile = file(readmeChecksumFilePath)
    outputs.file(readmeChecksumFile).withPropertyName("readmeChecksum")

    doLast("update readme") {
        fs.copy {
            from(readmeTemplateFile)
            rename { readmeFile.name }
            filteringCharset = "UTF-8"
            expand("majorVersion" to majorVersion)
            into(".")
        }
        readmeChecksumFile.writeText(calculateChecksum(readmeFile))
    }
}

abstract class ReadmeTask : DefaultTask() {
    @get:Inject
    abstract val fs: FileSystemOperations

    fun calculateChecksum(file: File) = MessageDigest.getInstance("SHA-256").let { sha256 ->
        sha256.digest(
            file
                .readLines()
                .joinToString("\n")
                .toByteArray()
        ).let {
            BigInteger(1, it)
                .toString(16)
                .padStart(sha256.digestLength * 2, '0')
        }
    }
}
