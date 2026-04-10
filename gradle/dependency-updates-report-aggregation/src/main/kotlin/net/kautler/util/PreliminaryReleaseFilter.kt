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

package net.kautler.util

import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentFilter
import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentSelectionWithCurrent

object PreliminaryReleaseFilter : ComponentFilter {
    override fun reject(candidate: ComponentSelectionWithCurrent): Boolean {
        val preliminaryReleaseRegex = Regex(
            """(?i)[.-](?:${
                listOf(
                    "alpha",
                    "beta",
                    "dev",
                    "rc",
                    "cr",
                    "m",
                    "preview",
                    "test",
                    "pr",
                    "pre",
                    "b",
                    "ea"
                ).joinToString("|")
            })[.\d-]*"""
        )
        return preliminaryReleaseRegex.containsMatchIn(candidate.candidate.version) &&
            !preliminaryReleaseRegex.containsMatchIn(candidate.currentVersion)
    }
}
