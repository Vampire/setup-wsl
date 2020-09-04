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

package net.kautler.util

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar

@Suppress("UNCHECKED_CAST")
operator fun <T : YamlNode> YamlMap.get(key: String) =
        entries
                .asSequence()
                .find { (it.key as? YamlScalar)?.content == key }
                ?.value as T?

fun YamlMap.getKey(value: YamlNode) =
        entries
                .asSequence()
                .find { it.value == value }
                ?.key
                ?.let { it as? YamlScalar }
                ?.content
