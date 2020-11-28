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

import Buffer
import NodeJS.get
import NullWritable
import ReserveCacheError
import ValidationError
import addPath
import cacheDir
import debug
import downloadTool
import endGroup
import exec
import extractZip
import find
import fs.`T$32`
import fs.`T$35`
import fs.`T$45`
import fs.existsSync
import fs.mkdtempSync
import fs.readdirSync
import fs.writeFileSync
import getInput
import info
import kotlinext.js.jsObject
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import mkdirP
import os.tmpdir
import path.path
import process
import restoreCache
import saveCache
import setFailed
import setOutput
import startGroup
import warning
import which

val distribution by lazy {
    val distributionId = getInput("distribution", jsObject {
        required = true
    }).trim()

    return@lazy requireNotNull(distributions[distributionId]) {
        "'${distributionId}' is not a valid distribution. Valid values: ${
            distributions.keys.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString()
        }"
    }
}

val installationNeeded = GlobalScope.async(start = LAZY) {
    exec(
            "wsl",
            arrayOf(
                    "--distribution",
                    distribution.id,
                    "true"
            ),
            jsObject {
                ignoreReturnCode = true
                outStream = NullWritable()
                errStream = NullWritable()
            }
    ).await() != 0
}

val toolCacheDir = GlobalScope.async(start = LAZY) {
    val fakeDir = mkdtempSync(path.join(tmpdir(), "setup_wsl_fake_dir_"), jsObject<`T$32`>())
    cacheDir(fakeDir, distribution.distributionName, "${distribution.version}").await()
}

val useCache by lazy {
    val input = getInput("use-cache", jsObject {
        required = true
    }).trim()

    when (input) {
        "true" -> true
        "false" -> false
        else -> error("'$input' is not a valid boolean for 'use-cache'. Valid values: true, false")
    }
}

val distributionDirectory = GlobalScope.async(start = LAZY) {
    var cacheDirectory = find(distribution.distributionName, "${distribution.version}")

    if (!cacheDirectory.isBlank()) {
        return@async cacheDirectory
    }

    cacheDirectory = toolCacheDir()

    val cacheKey = "distributionDirectory_${distribution.distributionName}_${distribution.version}"

    val restoredKey = if (useCache) restoreCache(arrayOf(cacheDirectory), cacheKey).await() else null
    if (restoredKey != null) {
        return@async cacheDirectory
    }

    val distributionDownload = downloadTool("${distribution.downloadUrl()}").await()
    var extractedDistributionDirectory = extractZip(distributionDownload).await()

    if (!existsSync(path.join(extractedDistributionDirectory, distribution.installerFile))) {
        extractedDistributionDirectory = readdirSync(extractedDistributionDirectory, jsObject<`T$35`>())
                .asFlow()
                .filter { it.contains("""(?<!_(?:scale-(?:100|125|150|400)|ARM64))\.appx$""".toRegex()) }
                .map { extractZip(path.join(extractedDistributionDirectory, it)).await() }
                .firstOrNull { existsSync(path.join(it, distribution.installerFile)) }
                ?: extractedDistributionDirectory
    }

    cacheDirectory = cacheDir(
            extractedDistributionDirectory,
            distribution.distributionName,
            "${distribution.version}"
    ).await()

    if (useCache) {
        try {
            saveCache(arrayOf(cacheDirectory), cacheKey).await()
        } catch (e: ValidationError) {
            throw e
        } catch (e: ReserveCacheError) {
            info(e.message ?: "$e")
        } catch (e: Throwable) {
            val message = e.message
            if (message == null) {
                if (e is Error) {
                    warning(e)
                } else {
                    warning(e.stackTraceToString())
                }
            } else {
                warning(message)
            }
        }
    }

    return@async cacheDirectory
}

val setAsDefault = GlobalScope.async(start = LAZY) {
    val input = getInput("set-as-default", jsObject {
        required = true
    }).trim()

    when (input) {
        "true" -> true
        "false" -> false
        "true | false" -> installationNeeded()
        else -> error("'$input' is not a valid boolean for 'set-as-default'. Valid values: true, false")
    }
}

val update by lazy {
    val input = getInput("update", jsObject {
        required = true
    }).trim()

    when (input) {
        "true" -> true
        "false" -> false
        else -> error("'$input' is not a valid boolean for 'update'. Valid values: true, false")
    }
}

val additionalPackages by lazy {
    getInput("additional-packages")
            .trim()
            .split("""\s+""".toRegex())
            .filterNot { it.isBlank() }
            .toTypedArray()
}

val wslShellUser by lazy {
    getInput("wsl-shell-user").trim()
}

val wslShellCommand by lazy {
    getInput("wsl-shell-command").trim()
}

val wslShellName by lazy {
    wslShellCommand
            .takeIf { it.isNotEmpty() }
            ?.split(' ', limit = 2)
            ?.first()
            ?: "bash"
}

val wslShellWrapperDirectory = path.join(process.env["RUNNER_TEMP"]!!, "wsl-shell-wrapper")

val wslShellWrapperPath by lazy {
    path.join(wslShellWrapperDirectory, "wsl-$wslShellName.bat")
}

val wslShellDistributionWrapperPath by lazy {
    path.join(wslShellWrapperDirectory, "wsl-${wslShellName}_${distribution.id.replace("[^a-zA-Z0-9.-]+".toRegex(), "_")}.bat")
}

suspend fun main() {
    runCatching {
        group("Verify Windows Environment", ::verifyWindowsEnvironment)

        if (installationNeeded()) {
            group("Install Distribution", ::installDistribution)
        }

        if (setAsDefault()) {
            group("Set Distribution as Default", ::setDistributionAsDefault)
        }

        if (update) {
            group("Update Distribution", distribution::update)
        }

        if (additionalPackages.isNotEmpty()) {
            group("Install Additional Packages", suspend { distribution.install(*additionalPackages) })
        }

        if (wslShellCommand.isNotEmpty()
                || !existsSync(wslShellWrapperPath)
                || !existsSync(wslShellDistributionWrapperPath)) {
            group("Write WSL Shell Wrapper", ::writeWslShellWrapper)
        }

        setOutput("wsl-shell-wrapper-path", wslShellWrapperPath)
        setOutput("wsl-shell-distribution-wrapper-path", wslShellDistributionWrapperPath)
    }.onFailure {
        debug(it.stackTraceToString())
        setFailed(it.message ?: "$it")
    }
}

suspend fun <T> group(name: String, fn: suspend () -> T): T {
    startGroup(name)
    try {
        return fn()
    } finally {
        endGroup()
    }
}

suspend fun verifyWindowsEnvironment() {
    check(process.platform == "win32") {
        "platform '${process.platform}' is not supported by this action, please verify your 'runs-on' setting"
    }
    check(which("wslconfig").await().isNotBlank()) {
        "This Windows environment does not have WSL enabled, please verify your 'runs-on' setting"
    }
}

suspend fun installDistribution() {
    exec(
            commandLine = """"${path.join(distributionDirectory(), distribution.installerFile)}"""",
            args = arrayOf("install", "--root"),
            options = jsObject {
                input = Buffer.from("")
            }
    ).await()
}

suspend fun setDistributionAsDefault() {
    exec(
            commandLine = "wslconfig",
            args = arrayOf("/setdefault", distribution.id)
    ).await()
}

suspend fun writeWslShellWrapper() {
    mkdirP(wslShellWrapperDirectory).await()

    val bashMissing = wslShellCommand.isEmpty()
            && (exec(
            "wsl",
            arrayOf(
                    "--distribution",
                    distribution.id,
                    "bash",
                    "-c",
                    "true"
            ),
            jsObject {
                ignoreReturnCode = true
            }
    ).await() != 0)

    if (wslShellUser.isNotEmpty()) {
        val wslShellUserExists = exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        distribution.id,
                        "id",
                        "-u",
                        wslShellUser
                ),
                jsObject {
                    ignoreReturnCode = true
                }
        ).await() == 0
        if (!wslShellUserExists) {
            exec(
                    "wsl",
                    arrayOf(
                            "--distribution",
                            distribution.id,
                            "useradd",
                            "-m",
                            "-p",
                            "4qBD5NWD3IkbU",
                            wslShellUser
                    )
            ).await()
        }
    }

    val scriptContent = (if (bashMissing) """
        @ECHO OFF

        ECHO Bash is not available by default in '${distribution.id}', please either add it to 'additional-packages' input or configure a different 'wsl-shell-command' >&2
        EXIT /B 1
    """ else """
        @ECHO OFF

        SETLOCAL

        IF '%1' EQU '' (
            REM wsl-shell
            GOTO INVALID_ARGUMENTS
        ) ELSE IF '%2' EQU '' (
            REM wsl-shell scriptFile
            ${if (wslShellUser.isEmpty()) "" else "SET wslShellUser=-u $wslShellUser"}
            SET scriptFile=%~1
        ) ELSE IF '%1' NEQ '-u' (
            REM wsl-shell user scriptFile
            GOTO INVALID_ARGUMENTS
        ) ELSE IF '%3' EQU '' (
            REM wsl-shell -u user
            GOTO INVALID_ARGUMENTS
        ) ELSE IF '%4' NEQ '' (
            REM wsl-shell -u user scriptFile foo
            GOTO INVALID_ARGUMENTS
        ) ELSE (
            REM wsl-shell -u user scriptFile
            SET wslShellUser=-u %~2
            SET scriptFile=%~3
        )

        IF NOT EXIST %scriptFile% GOTO INVALID_SCRIPT_FILE
        GOTO START

        :INVALID_ARGUMENTS
        ECHO Invalid arguments >&2
        GOTO USAGE

        :INVALID_SCRIPT_FILE
        ECHO Invalid script file "%scriptFile%" >&2
        GOTO USAGE

        :USAGE
        ECHO Usage: %~n0 [-u ^<user^>] ^<script file a.k.a. {0}^> >&2
        EXIT /B 1

        :START
        FOR /F "usebackq tokens=*" %%F IN (
            `wsl <wsl distribution parameter> -u root wslpath '%scriptFile%'`
        ) DO SET wslScriptFile=%%F
        wsl <wsl distribution parameter> -u root sed -i 's/\r$//' '%wslScriptFile%'

        wsl <wsl distribution parameter> %wslShellUser% ${
            when {
                wslShellCommand.isEmpty() ->
                    "bash --noprofile --norc -euo pipefail '%wslScriptFile%'"

                wslShellCommand.contains("{0}") ->
                    wslShellCommand.replace("{0}", "%wslScriptFile%")

                else ->
                    "$wslShellCommand '%wslScriptFile%'"
            }
        }
    """).trimIndent().lines().joinToString("\r\n")

    if (wslShellCommand.isNotEmpty() || !existsSync(wslShellWrapperPath)) {
        writeFileSync(
                wslShellWrapperPath,
                scriptContent.replace("<wsl distribution parameter> ", ""),
                jsObject<`T$45`>()
        )
    }


    if (wslShellCommand.isNotEmpty() || !existsSync(wslShellDistributionWrapperPath)) {
        writeFileSync(
                wslShellDistributionWrapperPath,
                scriptContent.replace("<wsl distribution parameter>", "--distribution ${distribution.id}"),
                jsObject<`T$45`>()
        )
    }

    addPath(wslShellWrapperDirectory)
}
