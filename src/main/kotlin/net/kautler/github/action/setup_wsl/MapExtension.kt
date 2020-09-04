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

package net.kautler.github.action.setup_wsl

import IHeaders
import kotlinext.js.jsObject
import tsstdlib.`T$2`

fun Map<String, Any>.asIHeaders() = asDynamic().unsafeCast<IHeaders>()

fun Map<String, Any>.`asT$2`() = asDynamic().unsafeCast<`T$2`>()

fun <V> Map<String, V>.asDynamic() = jsObject<dynamic> {
    for ((name, value) in this@asDynamic) {
        this[name] = value
    }
}
