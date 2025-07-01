/*
 * Copyright 2020-2025 Björn Kautler
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

import actions.cache.isFeatureAvailable
import actions.cache.restoreCache
import actions.cache.saveCache
import actions.core.InputOptions
import actions.core.addPath
import actions.core.debug
import actions.core.endGroup
import actions.core.getBooleanInput
import actions.core.getInput
import actions.core.isDebug
import actions.core.setFailed
import actions.core.setOutput
import actions.core.startGroup
import actions.core.warning
import actions.exec.ExecListeners
import actions.exec.ExecOptions
import actions.exec.exec
import actions.io.mkdirP
import actions.io.mv
import actions.io.which
import actions.tool.cache.cacheDir
import actions.tool.cache.downloadTool
import actions.tool.cache.find
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import node.buffer.Buffer
import node.buffer.BufferEncoding
import node.fs.exists
import node.fs.mkdtemp
import node.fs.readdir
import node.fs.writeFile
import node.os.tmpdir
import node.path.path
import node.process.Platform
import node.process.process
import nullwritable.NullWritable
import org.w3c.dom.url.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import actions.tool.cache.extractZip as toolCacheExtractZip

suspend fun wslOutput(vararg args: String): String {
    val stdoutBuilder = StringBuilder()
    val stdoutBuilderUtf16Le = StringBuilder()
    val stderrBuilder = StringBuilder()
    val stderrBuilderUtf16Le = StringBuilder()
    exec(
        commandLine = "wsl",
        args = args,
        options = ExecOptions(
            ignoreReturnCode = true,
            outStream = NullWritable(),
            errStream = NullWritable(),
            listeners = ExecListeners(
                stdout = {
                    stdoutBuilder.append(it)
                    stdoutBuilderUtf16Le.append(it.toString(BufferEncoding.utf16le))
                },
                stderr = {
                    stderrBuilder.append(it)
                    stderrBuilderUtf16Le.append(it.toString(BufferEncoding.utf16le))
                }
            )
        )
    )
    stdoutBuilder.append(stdoutBuilderUtf16Le)
    stdoutBuilder.append(stderrBuilder)
    stdoutBuilder.append(stderrBuilderUtf16Le)
    return stdoutBuilder.toString()
}

val wslHelp = GlobalScope.async(start = LAZY) {
    wslOutput("--help")
}

val distribution by lazy {
    val distributionId = getInput("distribution", InputOptions(required = true))

    return@lazy requireNotNull(distributions[distributionId]) {
        "'${distributionId}' is not a valid distribution. Valid values: ${
            distributions.keys.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString()
        }"
    }
}

val wslId = GlobalScope.async(start = LAZY) {
    if (isDebug()) {
        executeWslCommand(
            wslArguments = arrayOf("--list"),
            wslconfigArguments = arrayOf("/list")
        )
    }
    distribution.wslId
}

val wslInstallationNeeded = GlobalScope.async(start = LAZY) {
    wslOutput("--status").contains("is not installed")
}

val installationNeeded = GlobalScope.async(start = LAZY) {
    exec(
        commandLine = "wsl",
        args = arrayOf(
            "--distribution",
            distribution.wslId,
            "true"
        ),
        options = ExecOptions(
            ignoreReturnCode = true,
            outStream = NullWritable(),
            errStream = NullWritable()
        )
    ) != 0
}

val toolCacheDir = GlobalScope.async(start = LAZY) {
    val fakeDir = mkdtemp(path.join(tmpdir(), "setup_wsl_fake_dir_"))
    cacheDir(fakeDir, distribution.distributionName, "${distribution.version}")
}

val useCache by lazy {
    val result = when (val input = getInput("use-cache", InputOptions(required = true))) {
        "true" -> true
        "false" -> false
        "true | false" -> isFeatureAvailable()
        else -> error("'$input' is not a valid boolean for 'use-cache'. Valid values: true, false")
    }

    if (result && !isFeatureAvailable()) {
        val ghUrl = URL(process.env["GITHUB_SERVER_URL"] ?: "https://github.com", "")
        if (ghUrl.hostname.uppercase() != "GITHUB.COM") {
            warning("Caching is only supported on GHES version >= 3.5. If you are on version >= 3.5 please check with GHES admin if Actions cache service is enabled or not.")
        } else {
            warning("An internal error has occurred in cache backend. Please check https://www.githubstatus.com/ for any ongoing issue in actions.")
        }
        return@lazy false
    }

    return@lazy result
}

val distributionDirectory = GlobalScope.async(start = LAZY) {
    var cacheDirectory = find(distribution.distributionName, "${distribution.version}")

    if (cacheDirectory.isNotBlank()) {
        return@async cacheDirectory
    }

    cacheDirectory = toolCacheDir()

    val cacheKey = "2:distributionDirectory_${distribution.distributionName}_${distribution.version}"

    val restoredKey = if (useCache) restoreCache(arrayOf(cacheDirectory), cacheKey) else null
    if (restoredKey != null) {
        if (exists(path.join(cacheDirectory, distribution.installerFile))) {
            return@async cacheDirectory
        }
    }

    val distributionDownload = downloadTool("${distribution.downloadUrl()}")
    var extractedDistributionDirectory = extractZip(distributionDownload)

    if (!exists(path.join(extractedDistributionDirectory, distribution.installerFile))) {
        extractedDistributionDirectory = readdir(extractedDistributionDirectory)
            .asFlow()
            .filter { it.contains("""(?<!_(?:scale-(?:100|125|150|400)|ARM64))\.appx$""".toRegex()) }
            .map { extractZip(path.join(extractedDistributionDirectory, it)) }
            .firstOrNull { exists(path.join(it, distribution.installerFile)) }
            ?: error("'${distribution.installerFile}' not found for distribution '${distribution.userId}'")
    }

    cacheDirectory = cacheDir(
        extractedDistributionDirectory,
        distribution.distributionName,
        "${distribution.version}"
    )

    if (useCache) {
        saveCache(arrayOf(cacheDirectory), cacheKey)
    }

    return@async cacheDirectory
}

val wslConf by lazy {
    getInput("wsl-conf")
}

val setAsDefault = GlobalScope.async(start = LAZY) {
    when (val input = getInput("set-as-default", InputOptions(required = true))) {
        "true" -> true
        "false" -> false
        "true | false" -> installationNeeded()
        else -> error("'$input' is not a valid boolean for 'set-as-default'. Valid values: true, false")
    }
}

val update by lazy {
    getBooleanInput("update", InputOptions(required = true))
}

val additionalPackages by lazy {
    getInput("additional-packages")
        .split("""\s+""".toRegex())
        .filterNot { it.isBlank() }
        .toTypedArray()
}

val wslShellUser by lazy {
    getInput("wsl-shell-user")
}

val wslShellCommand by lazy {
    getInput("wsl-shell-command")
}

val wslVersion = GlobalScope.async(start = LAZY) {
    val input = getInput("wsl-version", InputOptions(required = true))
    input
        .toUIntOrNull()
        .also { wslVersion ->
            when (wslVersion) {
                null, 0u -> error("'$input' is not a valid positive integer for 'wsl-version'.")
                1u -> Unit
                else -> {
                    check(wslHelp().contains("--set-default-version")) {
                        "This Windows environment only has WSLv1 available but WSLv$wslVersion was requested, please verify your 'runs-on' and 'wsl-version' settings"
                    }
                    if (wslVersion > 2u) {
                        warning("WSLv$wslVersion is untested, if it works with your workflow please open an issue to get the version tested and this warning removed")
                    }
                }
            }
        }
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
    path.join(
        wslShellWrapperDirectory,
        "wsl-${wslShellName}_${distribution.userId.replace("[^a-zA-Z0-9.-]+".toRegex(), "_")}.bat"
    )
}

suspend fun main() {
    runCatching {
        group("Verify Windows Environment", ::verifyWindowsEnvironment)

        if (getInput("only safe actions").isEmpty()
            || !getBooleanInput("only safe actions")
        ) {
            // on windows-2025 WSL is not installed at all currently, so install it without distribution
            // work-around for https://github.com/actions/runner-images/issues/11265
            if (wslInstallationNeeded()) {
                group("Install WSL", ::installWsl)
            }

            if (installationNeeded()) {
                group("Install Distribution", ::installDistribution)
            }

            if (wslConf.isNotEmpty()) {
                group("Create or overwrite /etc/wsl.conf", ::adjustWslConf)
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
        }

        setOutput("wsl-shell-wrapper-path", wslShellWrapperPath)
        setOutput("wsl-shell-distribution-wrapper-path", wslShellDistributionWrapperPath)
    }.onFailure {
        debug(it.stackTraceToString())
        setFailed(it.message ?: "$it")
    }
}

suspend fun extractZip(archive: String): String {
    // work-around for https://github.com/actions/toolkit/issues/1319
    val archiveZip = "$archive.zip"
    mv(archive, archiveZip)
    return toolCacheExtractZip(archiveZip)
}

suspend fun executeWslCommand(
    wslArguments: Array<String>,
    wslconfigArguments: Array<String>? = null
) {
    if (wslHelp().contains(wslArguments.first())) {
        exec(
            commandLine = "wsl",
            args = wslArguments
        )
    } else if (wslconfigArguments != null) {
        exec(
            commandLine = "wslconfig",
            args = wslconfigArguments
        )
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
    check(process.platform == Platform.win32) {
        "platform '${process.platform}' is not supported by this action, please verify your 'runs-on' setting"
    }
    check(which("wsl").isNotBlank() || which("wslconfig").isNotBlank()) {
        "This Windows environment does not have WSL enabled, please verify your 'runs-on' setting"
    }
}

suspend fun installWsl() {
    exec(
        commandLine = "pwsh",
        args = arrayOf("-Command", """Start-Process wsl "--install --no-distribution""""),
        options = ExecOptions(ignoreReturnCode = true)
    )
    waitForWslStatusNotContaining("is not installed", 5.minutes)
}

suspend fun installDistribution() {
    executeWslCommand(
        wslArguments = arrayOf("--set-default-version", "${wslVersion()}")
    )

    if ((wslVersion() != 1u) && (process.env["ImageOS"] == "win22") && (process.env["RUNNER_ENVIRONMENT"] == "github-hosted")) {
        retry(10) {
            executeWslCommand(
                wslArguments = arrayOf("--update")
            )
        }
        waitForWslStatusNotContaining("WSL is finishing an upgrade...")
    }

    exec(
        commandLine = """"${path.join(distributionDirectory(), distribution.installerFile)}"""",
        args = arrayOf("install", "--root"),
        options = ExecOptions(input = Buffer.from(""))
    )
}

suspend fun waitForWslStatusNotContaining(
    text: String,
    duration: Duration = 30.seconds
) {
    (2..duration.inWholeSeconds)
        .asFlow()
        .onEach { delay(1.seconds) }
        .onStart { emit(1) }
        .map { wslOutput("--status") }
        .firstOrNull { !it.contains(text) }
}

suspend fun adjustWslConf() {
    exec(
        commandLine = "wsl",
        args = arrayOf(
            "--distribution", wslId(),
            "sh", "-c", "echo '$wslConf' >/etc/wsl.conf"
        )
    )
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
    mkdirP(wslShellWrapperDirectory)

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
        options = ExecOptions(ignoreReturnCode = true)
    ) != 0)

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
            options = ExecOptions(ignoreReturnCode = true)
        ) == 0
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
            )
        }
    }

    val scriptContent = (if (bashMissing) """
        @ECHO ${if (isDebug()) "ON" else "OFF"}

        ECHO Bash is not available by default in '${distribution.userId}', please either add it to 'additional-packages' input or configure a different 'wsl-shell-command' >&2
        EXIT /B 1
    """ else """
        @ECHO ${if (isDebug()) "ON" else "OFF"}

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

    addPath(wslShellWrapperDirectory)
}
