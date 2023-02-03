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

@file:OptIn(DelicateCoroutinesApi::class)

package net.kautler.github.action.setup_wsl

import NullWritable
import exec
import js.core.get
import js.core.jso
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import node.buffer.Buffer
import node.buffer.BufferEncoding.utf16le
import node.fs.PathLike
import node.fs.accessAsync
import node.fs.mkdtemp
import node.fs.readdir
import node.fs.writeFile
import node.os.tmpdir
import node.path.path
import node.process.Platform.win32
import node.process.process
import node.url.URL
import addPath as coreAddPath
import cacheDir as toolCacheCacheDir
import debug as coreDebug
import downloadTool as toolCacheDownloadTool
import endGroup as coreEndGroup
import extractZip as toolCacheExtractZip
import find as toolCacheFind
import getBooleanInput as coreGetBooleanInput
import getInput as coreGetInput
import isDebug as coreIsDebug
import isFeatureAvailable as cacheIsFeatureAvailable
import mkdirP as ioMkdirP
import mv as ioMv
import restoreCache as cacheRestoreCache
import saveCache as cacheSaveCache
import setFailed as coreSetFailed
import setOutput as coreSetOutput
import startGroup as coreStartGroup
import warning as coreWarning
import which as ioWhich

val wslHelp = GlobalScope.async(start = LAZY) {
    val stdoutBuilder = StringBuilder()
    val stdoutBuilderUtf16Le = StringBuilder()
    val stderrBuilder = StringBuilder()
    val stderrBuilderUtf16Le = StringBuilder()
    exec(
        commandLine = "wsl",
        args = arrayOf("--help"),
        options = jso {
            ignoreReturnCode = true
            outStream = NullWritable()
            errStream = NullWritable()
            listeners = jso {
                stdout = {
                    stdoutBuilder.append(it)
                    stdoutBuilderUtf16Le.append(it.toString(utf16le))
                }
                stderr = {
                    stderrBuilder.append(it)
                    stderrBuilderUtf16Le.append(it.toString(utf16le))
                }
            }
        }
    ).await()
    stdoutBuilder.append(stdoutBuilderUtf16Le)
    stdoutBuilder.append(stderrBuilder)
    stdoutBuilder.append(stderrBuilderUtf16Le)
    stdoutBuilder.toString()
}

val distribution by lazy {
    val distributionId = coreGetInput("distribution", jso {
        required = true
    })

    return@lazy requireNotNull(distributions[distributionId]) {
        "'${distributionId}' is not a valid distribution. Valid values: ${
            distributions.keys.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString()
        }"
    }
}

val wslId = GlobalScope.async(start = LAZY) {
    if (coreIsDebug()) {
        executeWslCommand(
            wslArguments = arrayOf("--list"),
            wslconfigArguments = arrayOf("/list")
        )
    }
    distribution.wslId
}

val installationNeeded = GlobalScope.async(start = LAZY) {
    exec(
        commandLine = "wsl",
        args = arrayOf(
            "--distribution",
            distribution.wslId,
            "true"
        ),
        options = jso {
            ignoreReturnCode = true
            outStream = NullWritable()
            errStream = NullWritable()
        }
    ).await() != 0
}

val toolCacheDir = GlobalScope.async(start = LAZY) {
    val fakeDir = mkdtemp(path.join(tmpdir(), "setup_wsl_fake_dir_"))
    toolCacheCacheDir(fakeDir, distribution.distributionName, "${distribution.version}").await()
}

val useCache by lazy {
    val input = coreGetInput("use-cache", jso {
        required = true
    })

    val result = when (input) {
        "true" -> true
        "false" -> false
        "true | false" -> cacheIsFeatureAvailable()
        else -> error("'$input' is not a valid boolean for 'use-cache'. Valid values: true, false")
    }

    if (result && !cacheIsFeatureAvailable()) {
        val ghUrl = URL(process.env["GITHUB_SERVER_URL"] ?: "https://github.com", "")
        if (ghUrl.hostname.uppercase() != "GITHUB.COM") {
            coreWarning("Caching is only supported on GHES version >= 3.5. If you are on version >= 3.5 please check with GHES admin if Actions cache service is enabled or not.")
        } else {
            coreWarning("An internal error has occurred in cache backend. Please check https://www.githubstatus.com/ for any ongoing issue in actions.")
        }
        return@lazy false
    }

    return@lazy result
}

val distributionDirectory = GlobalScope.async(start = LAZY) {
    var cacheDirectory = toolCacheFind(distribution.distributionName, "${distribution.version}")

    if (cacheDirectory.isNotBlank()) {
        return@async cacheDirectory
    }

    cacheDirectory = toolCacheDir()

    val cacheKey = "2:distributionDirectory_${distribution.distributionName}_${distribution.version}"

    val restoredKey = if (useCache) cacheRestoreCache(arrayOf(cacheDirectory), cacheKey).await() else null
    if (restoredKey != null) {
        if (exists(path.join(cacheDirectory, distribution.installerFile))) {
            return@async cacheDirectory
        }
    }

    val distributionDownload = toolCacheDownloadTool("${distribution.downloadUrl()}").await()
    var extractedDistributionDirectory = extractZip(distributionDownload)

    if (!exists(path.join(extractedDistributionDirectory, distribution.installerFile))) {
        extractedDistributionDirectory = readdir(extractedDistributionDirectory)
            .asFlow()
            .filter { it.contains("""(?<!_(?:scale-(?:100|125|150|400)|ARM64))\.appx$""".toRegex()) }
            .map { extractZip(path.join(extractedDistributionDirectory, it)) }
            .firstOrNull { exists(path.join(it, distribution.installerFile)) }
            ?: error("'${distribution.installerFile}' not found for distribution '${distribution.userId}'")
    }

    cacheDirectory = toolCacheCacheDir(
        extractedDistributionDirectory,
        distribution.distributionName,
        "${distribution.version}"
    ).await()

    if (useCache) {
        cacheSaveCache(arrayOf(cacheDirectory), cacheKey).await()
    }

    return@async cacheDirectory
}

val wslConf by lazy {
    coreGetInput("wsl-conf")
}

val setAsDefault = GlobalScope.async(start = LAZY) {
    val input = coreGetInput("set-as-default", jso {
        required = true
    })

    when (input) {
        "true" -> true
        "false" -> false
        "true | false" -> installationNeeded()
        else -> error("'$input' is not a valid boolean for 'set-as-default'. Valid values: true, false")
    }
}

val update by lazy {
    coreGetBooleanInput("update", jso {
        required = true
    })
}

val additionalPackages by lazy {
    coreGetInput("additional-packages")
        .split("""\s+""".toRegex())
        .filterNot { it.isBlank() }
        .toTypedArray()
}

val wslShellUser by lazy {
    coreGetInput("wsl-shell-user")
}

val wslShellCommand by lazy {
    coreGetInput("wsl-shell-command")
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
    path.join(wslShellWrapperDirectory, "wsl-${wslShellName}_${distribution.userId.replace("[^a-zA-Z0-9.-]+".toRegex(), "_")}.bat")
}

suspend fun main() {
    runCatching {
        group("Verify Windows Environment", ::verifyWindowsEnvironment)

        if (installationNeeded()) {
            group("Install Distribution", ::installDistribution)
        }

        if (wslConf.isNotEmpty()) {
            group("Create /etc/wsl.conf", ::createWslConf)
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
            || !exists(wslShellWrapperPath)
            || !exists(wslShellDistributionWrapperPath)
        ) {
            group("Write WSL Shell Wrapper", ::writeWslShellWrapper)
        }

        coreSetOutput("wsl-shell-wrapper-path", wslShellWrapperPath)
        coreSetOutput("wsl-shell-distribution-wrapper-path", wslShellDistributionWrapperPath)
    }.onFailure {
        coreDebug(it.stackTraceToString())
        coreSetFailed(it.message ?: "$it")
    }
}

suspend fun extractZip(archive: String): String {
    // work-around for https://github.com/actions/toolkit/issues/1319
    val archiveZip = "$archive.zip"
    ioMv(archive, archiveZip).await()
    return toolCacheExtractZip(archiveZip).await()
}

suspend fun executeWslCommand(
    wslArguments: Array<String>,
    wslconfigArguments: Array<String>? = null
) {
    if (wslHelp().contains(wslArguments.first())) {
        exec(
            commandLine = "wsl",
            args = wslArguments
        ).await()
    } else if (wslconfigArguments != null) {
        exec(
            commandLine = "wslconfig",
            args = wslconfigArguments
        ).await()
    }
}

suspend fun <T> group(name: String, fn: suspend () -> T): T {
    coreStartGroup(name)
    try {
        return fn()
    } finally {
        coreEndGroup()
    }
}

suspend fun verifyWindowsEnvironment() {
    check(process.platform == win32) {
        "platform '${process.platform}' is not supported by this action, please verify your 'runs-on' setting"
    }
    check(ioWhich("wsl").await().isNotBlank() || ioWhich("wslconfig").await().isNotBlank()) {
        "This Windows environment does not have WSL enabled, please verify your 'runs-on' setting"
    }
}

suspend fun installDistribution() {
    executeWslCommand(
        wslArguments = arrayOf("--set-default-version", "1")
    )
    exec(
        commandLine = """"${path.join(distributionDirectory(), distribution.installerFile)}"""",
        args = arrayOf("install", "--root"),
        options = jso {
            input = Buffer.from("")
        }
    ).await()
}

suspend fun createWslConf() {
    exec(
        commandLine = "wsl",
        args = arrayOf(
            "--distribution", wslId(),
            "sh", "-c", "echo '$wslConf' >/etc/wsl.conf"
        )
    ).await()
    executeWslCommand(
        wslArguments = arrayOf("--terminate", wslId()),
        wslconfigArguments = arrayOf("/terminate", wslId())
    )
}

suspend fun setDistributionAsDefault() {
    executeWslCommand(
        wslArguments = arrayOf("--set-default", wslId()),
        wslconfigArguments = arrayOf("/setdefault", wslId())
    )
}

suspend fun writeWslShellWrapper() {
    ioMkdirP(wslShellWrapperDirectory).await()

    val bashMissing = wslShellCommand.isEmpty()
            && (exec(
        commandLine = "wsl",
        args = arrayOf(
            "--distribution",
            wslId(),
            "bash",
            "-c",
            "true"
        ),
        options = jso {
            ignoreReturnCode = true
        }
    ).await() != 0)

    if (wslShellUser.isNotEmpty()) {
        val wslShellUserExists = exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId(),
                "id",
                "-u",
                wslShellUser
            ),
            options = jso {
                ignoreReturnCode = true
            }
        ).await() == 0
        if (!wslShellUserExists) {
            exec(
                commandLine = "wsl",
                args = arrayOf(
                    "--distribution",
                    wslId(),
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
        @ECHO ${if (coreIsDebug()) "ON" else "OFF"}

        ECHO Bash is not available by default in '${distribution.userId}', please either add it to 'additional-packages' input or configure a different 'wsl-shell-command' >&2
        EXIT /B 1
    """ else """
        @ECHO ${if (coreIsDebug()) "ON" else "OFF"}

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

    if (wslShellCommand.isNotEmpty() || !exists(wslShellWrapperPath)) {
        writeFile(
            wslShellWrapperPath,
            scriptContent.replace("<wsl distribution parameter> ", "")
        )
    }

    if (wslShellCommand.isNotEmpty() || !exists(wslShellDistributionWrapperPath)) {
        writeFile(
            wslShellDistributionWrapperPath,
            scriptContent.replace("<wsl distribution parameter>", "--distribution ${wslId()}")
        )
    }

    coreAddPath(wslShellWrapperDirectory)
}

suspend fun exists(path: PathLike) = accessAsync(path)
    .then { true }
    .catch { false }
    .await()
