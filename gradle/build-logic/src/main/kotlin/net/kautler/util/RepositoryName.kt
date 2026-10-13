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

import net.kautler.util.RepositoryName.Parameters
import org.ajoberstar.grgit.Grgit
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

abstract class RepositoryName : ValueSource<String, Parameters> {
    override fun obtain() = parameters
        .explictGithubRepositoryName
        .orNull
        ?: runCatching {
            Grgit.open {
                currentDir = parameters.projectDirectory.get().asFile
            }
        }
            .getOrNull()
            ?.use {
                it
                    .remote
                    .list()
                    .find {
                        (it.name == "origin") &&
                            it.url.lowercase().contains("github.com")
                    }
                    ?.url
                    ?.substringAfter("github.com")
                    ?.drop(1)
                    ?.removeSuffix(".git")
            }
        ?: error("Could not determine GitHub repository name")

    interface Parameters : ValueSourceParameters {
        val explictGithubRepositoryName: Property<String>
        val projectDirectory: DirectoryProperty
    }
}
