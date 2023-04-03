/*
 * Copyright 2020-2024 Björn Kautler
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

import js.core.jso
import kotlinx.coroutines.await
import node.fs.MakeDirectoryOptions
import node.fs.WriteFileOptions
import node.fs.mkdir
import node.fs.writeFile
import node.path.path
import node.process.process

suspend fun main() {
    runCatching {
        val (input, output) = process.argv.filterIndexed { i, _ -> i > 1 }
        val result = ncc(input, jso {
            sourceMap = true
            license = "LICENSES"
        }).await()

        mkdir(output, jso<MakeDirectoryOptions> {
            recursive = true
        })
        writeFile(path.join(output, "index.js"), result.code)
        result.map?.also { writeFile(path.join(output, "index.js.map"), it) }

        result.assets?.forEach { (assetFileName, asset) ->
            val assetFilePath = path.join(output, assetFileName)
            mkdir(path.dirname(assetFilePath), jso<MakeDirectoryOptions> {
                recursive = true
            })
            writeFile(assetFilePath, asset.source, jso<WriteFileOptions> {
                mode = asset.permissions
            })
        }
    }.onFailure {
        console.error(it)
        process.exit(1)
    }
}
