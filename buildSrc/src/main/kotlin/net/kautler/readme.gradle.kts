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

package net.kautler

import java.security.MessageDigest

plugins {
    `lifecycle-base`
}

val majorVersion: String by project
val readmeTemplateFilePath = "readme/README_template.md"
val readmeFilePath = "README.md"
val readmeChecksumFilePath = "readme/README.md.sha256"

val readmeCopySpec = copySpec {
    from(readmeTemplateFilePath)
    rename { readmeFilePath }
    filteringCharset = "UTF-8"
    expand("majorVersion" to majorVersion)
}

val verifyReadme by tasks.registering {
    inputs.property("majorVersion", majorVersion)
    inputs.file(readmeTemplateFilePath).withPropertyName("readmeTemplate")
    inputs.files(readmeFilePath).withPropertyName("readme")
    inputs.file(readmeChecksumFilePath).withPropertyName("readmeChecksum")

    doLast("verify readme") {
        if (!file(readmeFilePath).exists() || file(readmeChecksumFilePath).readText() != calculateReadmeChecksum()) {
            // do not use hasTask() as this requires realization of the task that maybe is not necessary
            if (gradle.taskGraph.allTasks.any { it.name == "updateReadme" }) {
                error("The README.md file was tampered with manually, "
                        + "if you want to overwrite it, add \"-x $name\" to your Gradle call")
            }
            error("The README.md file was tampered with manually")
        }

        copy {
            with(readmeCopySpec)
            into(temporaryDir)
        }
    }
}

tasks.check {
    dependsOn(verifyReadme)
}

tasks.register("updateReadme") {
    dependsOn(verifyReadme)

    inputs.property("majorVersion", majorVersion)
    inputs.file(readmeTemplateFilePath).withPropertyName("readmeTemplate")
    outputs.file(readmeFilePath).withPropertyName("readme")
    outputs.file(readmeChecksumFilePath).withPropertyName("readmeChecksum")

    doLast("update readme") {
        copy {
            with(readmeCopySpec)
            into(".")
        }
        file(readmeChecksumFilePath).writeText(calculateReadmeChecksum())
    }
}

fun calculateReadmeChecksum() = MessageDigest.getInstance("SHA-256").let { sha256 ->
    sha256.digest(
            file("README.md")
                    .readLines()
                    .joinToString("\n")
                    .toByteArray()
    ).let {
        BigInteger(1, it)
                .toString(16)
                .padStart(sha256.digestLength * 2, '0')
    }
}
