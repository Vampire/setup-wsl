/*
 * Copyright 2026 Björn Kautler
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

import com.github.benmanes.gradle.versions.reporter.result.Result
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import net.kautler.dao.result.ResultSerializer
import net.kautler.util.PreliminaryReleaseFilter
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.VERIFICATION
import org.gradle.api.attributes.VerificationType.VERIFICATION_TYPE_ATTRIBUTE

plugins {
    id("com.github.ben-manes.versions")
}

val dependencyUpdatesResult = configurations.consumable("dependencyUpdatesResult") {
    attributes {
        attribute(CATEGORY_ATTRIBUTE, named(VERIFICATION))
        attribute(VERIFICATION_TYPE_ATTRIBUTE, named("dependency-updates-result"))
    }
}

tasks.dependencyUpdates {
    val reportFile = file("build/dependencyUpdates/dependencyUpdatesReport.json")
    outputs.file(reportFile).withPropertyName("reportFile")

    checkForGradleUpdate = false
    checkConstraints = true
    rejectVersionIf(PreliminaryReleaseFilter)

    outputFormatter = closureOf<Result> {
        reportFile
            .apply { parentFile.mkdirs() }
            .outputStream()
            .use { reportFileStream ->
                val json = Json { prettyPrint = true }
                @OptIn(ExperimentalSerializationApi::class)
                json.encodeToStream(ResultSerializer, this, reportFileStream)
            }
    }
}

artifacts {
    add(dependencyUpdatesResult.name, tasks.dependencyUpdates)
}
