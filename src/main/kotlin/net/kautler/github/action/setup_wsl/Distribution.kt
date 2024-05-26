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

package net.kautler.github.action.setup_wsl

import SemVer
import actions.core.debug
import actions.core.info
import actions.core.isDebug
import actions.exec.exec
import actions.http.client.HttpClient
import js.core.jso
import js.core.recordOf
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import org.w3c.dom.url.URL
import RangeOptions as SemVerRangeOptions

val distributions = listOf(
    Alpine,
    Debian,
    Kali,
    OpenSuseLeap15_2,
    Ubuntu1604,
    Ubuntu1804,
    Ubuntu2004,
    Ubuntu2204,
    Ubuntu2404
).associateBy { it.userId }

sealed class Distribution(
    val wslId: String,
    val distributionName: String,
    val version: SemVer,
    private val _downloadUrl: URL?,
    private val productId: String?,
    val installerFile: String,
    val userId: String = wslId
) {
    @DelicateCoroutinesApi
    val downloadUrl = GlobalScope.async(start = LAZY) {
        if (_downloadUrl != null) {
            return@async _downloadUrl
        }

        return@async retry(5) {
            val response = HttpClient().post(
                requestUrl = "https://store.rg-adguard.net/api/GetFiles",
                data = "type=ProductId&url=$productId",
                additionalHeaders = recordOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "User-Agent" to "Setup WSL GitHub Action"
                )
            ).await()

            if (response.message.statusCode != 200) {
                if (isDebug()) {
                    val echoResponse = HttpClient().post(
                        requestUrl = "https://echo.free.beeceptor.com/api/GetFiles",
                        data = "type=ProductId&url=$productId",
                        additionalHeaders = recordOf(
                            "Content-Type" to "application/x-www-form-urlencoded",
                            "User-Agent" to "Setup WSL GitHub Action"
                        )
                    ).await()
                    if (echoResponse.message.statusCode == 200) {
                        debug("Request:\n${echoResponse.readBody().await()}")
                    } else {
                        debug("Could not get echo response (statusCode: ${echoResponse.message.statusCode} / statusMessage: ${echoResponse.message.statusMessage})")
                    }

                    val responseMessage = JSON.stringify(
                        recordOf(
                            "httpVersion" to response.message.httpVersion,
                            "headers" to response.message.headers,
                            "trailers" to response.message.trailers,
                            "method" to (response.message.method ?: "<unknown>"),
                            "url" to (response.message.url ?: "<unknown>"),
                            "statusCode" to (response.message.statusCode ?: "<unknown>"),
                            "statusMessage" to (response.message.statusMessage ?: "<unknown>"),
                            "body" to response.readBody().await()
                        ),
                        space = 2
                    )
                    debug("Response:\n$responseMessage")
                }
                error("Could not determine download URL (statusCode: ${response.message.statusCode} / statusMessage: ${response.message.statusMessage})")
            }

            val body = response.readBody().await()
            val downloadLinkAnchorMatch =
                """<a [^>]*href="(?<url>[^"]+)"[^>]*>[^<]*\.appx(?:bundle)?</a>""".toRegex().find(body)
                    ?: error("Could not determine download URL from:\n$body")

            return@retry URL(downloadLinkAnchorMatch.groups[1]!!.value)
        }
    }

    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String
    ) : this(wslId, distributionName, version, downloadUrl, null, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String,
    ) : this(wslId, distributionName, version, downloadUrl, null, installerFile, userId)

    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        productId: String,
        installerFile: String
    ) : this(wslId, distributionName, version, null, productId, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        productId: String,
        installerFile: String
    ) : this(wslId, distributionName, version, null, productId, installerFile, userId)

    abstract suspend fun update()

    abstract suspend fun install(vararg packages: String)
}

abstract class AptGetBasedDistribution : Distribution {
    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String
    ) : super(wslId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String
    ) : super(wslId, userId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        productId: String,
        installerFile: String
    ) : super(wslId, distributionName, version, productId, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        productId: String,
        installerFile: String
    ) : super(wslId, userId, distributionName, version, productId, installerFile)

    private suspend fun refresh() {
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "apt-get",
                "update"
            ),
            options = jso {
                env = recordOf(
                    "DEBIAN_FRONTEND" to "noninteractive",
                    "WSLENV" to "DEBIAN_FRONTEND/u"
                )
            }
        )
    }

    override suspend fun update() {
        refresh()
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "apt-get",
                "upgrade",
                "--yes"
            ),
            options = jso {
                env = recordOf(
                    "DEBIAN_FRONTEND" to "noninteractive",
                    "WSLENV" to "DEBIAN_FRONTEND/u"
                )
            }
        )
    }

    override suspend fun install(vararg packages: String) {
        refresh()
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "apt-get",
                "install",
                "--yes",
                "--no-install-recommends",
                *packages
            ),
            options = jso {
                env = recordOf(
                    "DEBIAN_FRONTEND" to "noninteractive",
                    "WSLENV" to "DEBIAN_FRONTEND/u"
                )
            }
        )
    }
}

object Ubuntu2404 : AptGetBasedDistribution(
    wslId = "Ubuntu-24.04",
    distributionName = "Ubuntu",
    version = SemVer("24.4.0", jso<SemVerRangeOptions>()),
    // work-around for missing shortlink on https://learn.microsoft.com/en-us/windows/wsl/install-manual#downloading-distributions
    //downloadUrl = URL("https://aka.ms/wslubuntu2404"),
    productId = "9nz3klhxdjp5",
    installerFile = "ubuntu2404.exe"
)

object Ubuntu2204 : AptGetBasedDistribution(
    wslId = "Ubuntu",
    userId = "Ubuntu-22.04",
    distributionName = "Ubuntu",
    version = SemVer("22.4.0", jso<SemVerRangeOptions>()),
    downloadUrl = URL("https://aka.ms/wslubuntu2204"),
    installerFile = "ubuntu.exe"
)

object Ubuntu2004 : AptGetBasedDistribution(
    wslId = "Ubuntu",
    userId = "Ubuntu-20.04",
    distributionName = "Ubuntu",
    version = SemVer("20.4.0", jso<SemVerRangeOptions>()),
    downloadUrl = URL("https://aka.ms/wslubuntu2004"),
    installerFile = "ubuntu.exe"
)

object Ubuntu1804 : AptGetBasedDistribution(
    wslId = "Ubuntu-18.04",
    distributionName = "Ubuntu",
    version = SemVer("18.4.0", jso<SemVerRangeOptions>()),
    downloadUrl = URL("https://aka.ms/wsl-ubuntu-1804"),
    installerFile = "ubuntu1804.exe"
)

object Ubuntu1604 : AptGetBasedDistribution(
    wslId = "Ubuntu-16.04",
    distributionName = "Ubuntu",
    version = SemVer("16.4.0", jso<SemVerRangeOptions>()),
    downloadUrl = URL("https://aka.ms/wsl-ubuntu-1604"),
    installerFile = "ubuntu1604.exe"
)

object Debian : AptGetBasedDistribution(
    wslId = "Debian",
    distributionName = "Debian",
    version = SemVer("1.0.0", jso<SemVerRangeOptions>()),
    downloadUrl = URL("https://aka.ms/wsl-debian-gnulinux"),
    installerFile = "debian.exe"
)

object Kali : AptGetBasedDistribution(
    wslId = "kali-linux",
    distributionName = "Kali",
    version = SemVer("1.0.0", jso<SemVerRangeOptions>()),
    downloadUrl = URL("https://aka.ms/wsl-kali-linux-new"),
    installerFile = "kali.exe"
)

abstract class ZypperBasedDistribution : Distribution {
    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String
    ) : super(wslId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String
    ) : super(wslId, userId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        productId: String,
        installerFile: String
    ) : super(wslId, distributionName, version, productId, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        productId: String,
        installerFile: String
    ) : super(wslId, userId, distributionName, version, productId, installerFile)

    protected open suspend fun refresh() {
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "zypper",
                "--non-interactive",
                "refresh"
            )
        )
    }

    override suspend fun update() {
        refresh()
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "zypper",
                "--non-interactive",
                "update"
            )
        )
    }

    override suspend fun install(vararg packages: String) {
        refresh()
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "zypper",
                "--non-interactive",
                "install",
                *packages
            )
        )
    }
}

object OpenSuseLeap15_2 : ZypperBasedDistribution(
    wslId = "openSUSE-Leap-15.2",
    distributionName = "openSUSE Leap",
    version = SemVer("15.2.0", jso<SemVerRangeOptions>()),
    downloadUrl = URL("https://aka.ms/wsl-opensuseleap15-2"),
    installerFile = "openSUSE-Leap-15.2.exe"
) {
    override suspend fun refresh() {
        retry(5) {
            super.refresh()
        }
    }
}

abstract class ApkBasedDistribution : Distribution {
    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String
    ) : super(wslId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String
    ) : super(wslId, userId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        productId: String,
        installerFile: String
    ) : super(wslId, distributionName, version, productId, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        productId: String,
        installerFile: String
    ) : super(wslId, userId, distributionName, version, productId, installerFile)

    private suspend fun refresh() {
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "apk",
                "update"
            )
        )
    }

    override suspend fun update() {
        refresh()
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "apk",
                "upgrade"
            )
        )
    }

    override suspend fun install(vararg packages: String) {
        refresh()
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "apk",
                "add",
                *packages
            )
        )
    }
}

object Alpine : ApkBasedDistribution(
    wslId = "Alpine",
    distributionName = "Alpine",
    version = SemVer("1.0.3", jso<SemVerRangeOptions>()),
    productId = "9p804crf0395",
    installerFile = "Alpine.exe"
)

private suspend inline fun <T> retry(amount: Int, crossinline block: suspend () -> T): T {
    (1..amount).map { i ->
        runCatching {
            return block()
        }.onFailure {
            if (i != 5) {
                debug(it.stackTraceToString())
                info("Failure happened, retrying (${it.message ?: it})")
            }
        }
    }.last().getOrThrow<Nothing>()
}
