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

package net.kautler.nccpacker

import Buffer
import kotlinext.js.Object
import kotlin.js.Promise

@JsModule("@vercel/ncc")
external fun ncc(input: String, options: NccOptions = definedExternally): Promise<NccResult>

external interface NccOptions {
    var cache: dynamic
    var externals: List<String>
    var filterAssetBase: String
    var minify: Boolean
    var sourceMap: Boolean
    var sourceMapBasePrefix: String
    var sourceMapRegister: Boolean
    var license: String
    var v8cache: Boolean
    var quiet: Boolean
    var debugLog: Boolean
    var transpileOnly: Boolean
    var target: String
}

external interface NccResult {
    val code: String
    val map: String?
    val assets: AssetMap?
}

external interface AssetMap

inline operator fun AssetMap.get(key: String) = asDynamic()[key].unsafeCast<Asset>()

inline operator fun AssetMap.set(key: String, value: Asset) {
    asDynamic()[key] = value
}

inline operator fun AssetMap.iterator() = Object.keys(this).map { it to this[it] }.iterator()

inline fun AssetMap.forEach(action: (Pair<String, Asset>) -> Unit) {
    for (element in this) {
        action(element)
    }
}

external interface Asset {
    val source: Buffer
    val permissions: Number?
}
