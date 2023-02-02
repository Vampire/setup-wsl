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

package net.kautler.dao.action

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Step(
    val run: String? = null,
    val shell: String? = null,
    val uses: String? = null,
    val with: Map<String, String>? = null,
    val name: String? = null,
    val id: String? = null,
    @SerialName("if")
    val condition: String? = null,
    val env: Map<String, String>? = null,
    @SerialName("working-directory")
    val workingDirectory: String? = null
) {
    init {
        require((run == null) || (uses == null)) {
            "'run' and 'uses' are mutually exclusive"
        }
        require((run != null) || (uses != null)) {
            "one of 'run' or 'uses' is required"
        }
        require((run == null) || (shell != null)) {
            "'shell' is required if 'run' is set"
        }
    }
}
