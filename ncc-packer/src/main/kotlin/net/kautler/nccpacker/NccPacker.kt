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

import fs.MakeDirectoryOptions
import fs.`T$45`
import fs.mkdirSync
import fs.writeFileSync
import kotlinext.js.jsObject
import kotlinx.coroutines.await
import path.path
import process

suspend fun main() {
    runCatching {
        val (input, output) = process.argv.filterIndexed { i, _ -> i > 1 }
        val result = ncc(input, jsObject {
            sourceMap = true
            license = "LICENSES"
        }).await()

        mkdirSync(output, jsObject<MakeDirectoryOptions> {
            recursive = true
        })
        writeFileSync(path.join(output, "index.js"), result.code, jsObject<`T$45`>())
        result.map?.also { writeFileSync(path.join(output, "index.js.map"), it, jsObject<`T$45`>()) }

        result.assets?.forEach { (assetFileName, asset) ->
            val assetFilePath = path.join(output, assetFileName)
            mkdirSync(path.dirname(assetFilePath), jsObject<MakeDirectoryOptions> {
                recursive = true
            })
            writeFileSync(assetFilePath, asset.source, jsObject<`T$45`> {
                mode = asset.permissions
            })
        }
    }.onFailure {
        console.error(it)
        process.exit(1)
    }
}
