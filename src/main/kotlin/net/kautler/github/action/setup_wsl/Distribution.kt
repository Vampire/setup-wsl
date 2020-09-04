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

import HttpClient
import Options
import SemVer
import exec
import kotlinext.js.jsObject
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import org.w3c.dom.url.URL

val distributions = listOf(
        Alpine,
        Debian,
        Kali,
        OpenSuseLeap15_2,
        Ubuntu1604,
        Ubuntu1804,
        Ubuntu2004
).map { it.id to it }.toMap()

sealed class Distribution(
        val id: String,
        val distributionName: String,
        val version: SemVer,
        private val _downloadUrl: URL?,
        private val productId: String?,
        val installerFile: String
) {
    val downloadUrl = GlobalScope.async(start = LAZY) {
        if (_downloadUrl != null) {
            return@async _downloadUrl
        }

        val response = HttpClient().post(
                requestUrl = "https://store.rg-adguard.net/api/GetFiles",
                data = "type=ProductId&url=$productId",
                additionalHeaders = mapOf(
                        "Content-Type" to "application/x-www-form-urlencoded"
                ).asIHeaders()
        ).await()

        if (response.message.statusCode != 200) {
            error("Could not determine download URL (statusCode: ${response.message.statusCode} / statusMessage: ${response.message.statusMessage})")
        }

        val body = response.readBody().await()
        val downloadLinkAnchorMatch =
                """<a [^>]*href="(?<url>[^"]+)"[^>]*>[^<]*\.appx(?:bundle)?</a>""".toRegex().find(body)
                        ?: error("Could not determine download URL from:\n$body")

        return@async URL(downloadLinkAnchorMatch.groups[1]!!.value)
    }

    constructor(
            id: String,
            distributionName: String,
            version: SemVer,
            downloadUrl: URL,
            installerFile: String
    ) : this(id, distributionName, version, downloadUrl, null, installerFile)

    constructor(
            id: String,
            distributionName: String,
            version: SemVer,
            productId: String,
            installerFile: String
    ) : this(id, distributionName, version, null, productId, installerFile)

    abstract suspend fun update()

    abstract suspend fun install(vararg packages: String)
}

abstract class AptGetBasedDistribution : Distribution {
    constructor(
            id: String,
            distributionName: String,
            version: SemVer,
            downloadUrl: URL,
            installerFile: String
    ) : super(id, distributionName, version, downloadUrl, installerFile)

    constructor(
            id: String,
            distributionName: String,
            version: SemVer,
            productId: String,
            installerFile: String
    ) : super(id, distributionName, version, productId, installerFile)

    private suspend fun refresh() {
        exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        id,
                        "apt-get",
                        "update"
                ),
                jsObject {
                    env = mapOf(
                            "DEBIAN_FRONTEND" to "noninteractive",
                            "WSLENV" to "DEBIAN_FRONTEND/u"
                    ).`asT$2`()
                }
        ).await()
    }

    override suspend fun update() {
        refresh()
        exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        id,
                        "apt-get",
                        "upgrade",
                        "--yes"
                ),
                jsObject {
                    env = mapOf(
                            "DEBIAN_FRONTEND" to "noninteractive",
                            "WSLENV" to "DEBIAN_FRONTEND/u"
                    ).`asT$2`()
                }
        ).await()
    }

    override suspend fun install(vararg packages: String) {
        refresh()
        exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        id,
                        "apt-get",
                        "install",
                        "--yes",
                        "--no-install-recommends",
                        *packages
                ),
                jsObject {
                    env = mapOf(
                            "DEBIAN_FRONTEND" to "noninteractive",
                            "WSLENV" to "DEBIAN_FRONTEND/u"
                    ).`asT$2`()
                }
        ).await()
    }
}

object Ubuntu2004 : AptGetBasedDistribution(
        id = "Ubuntu-20.04",
        distributionName = "Ubuntu",
        version = SemVer("20.4.0", jsObject<Options>()),
        downloadUrl = URL("https://aka.ms/wslubuntu2004"),
        installerFile = "ubuntu2004.exe"
)

object Ubuntu1804 : AptGetBasedDistribution(
        id = "Ubuntu-18.04",
        distributionName = "Ubuntu",
        version = SemVer("18.4.0", jsObject<Options>()),
        downloadUrl = URL("https://aka.ms/wsl-ubuntu-1804"),
        installerFile = "ubuntu1804.exe"
)

object Ubuntu1604 : AptGetBasedDistribution(
        id = "Ubuntu-16.04",
        distributionName = "Ubuntu",
        version = SemVer("16.4.0", jsObject<Options>()),
        downloadUrl = URL("https://aka.ms/wsl-ubuntu-1604"),
        installerFile = "ubuntu1604.exe"
)

object Debian : AptGetBasedDistribution(
        id = "Debian",
        distributionName = "Debian",
        version = SemVer("1.0.0", jsObject<Options>()),
        downloadUrl = URL("https://aka.ms/wsl-debian-gnulinux"),
        installerFile = "debian.exe"
)

object Kali : AptGetBasedDistribution(
        id = "kali-linux",
        distributionName = "Kali",
        version = SemVer("1.0.0", jsObject<Options>()),
        productId = "9pkr34tncv07",
        installerFile = "kali.exe"
)

abstract class ZypperBasedDistribution : Distribution {
    constructor(
            id: String,
            distributionName: String,
            version: SemVer,
            downloadUrl: URL,
            installerFile: String
    ) : super(id, distributionName, version, downloadUrl, installerFile)

    constructor(
            id: String,
            distributionName: String,
            version: SemVer,
            productId: String,
            installerFile: String
    ) : super(id, distributionName, version, productId, installerFile)

    private suspend fun refresh() {
        exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        id,
                        "zypper",
                        "--non-interactive",
                        "refresh"
                )
        ).await()
    }

    override suspend fun update() {
        refresh()
        exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        id,
                        "zypper",
                        "--non-interactive",
                        "update"
                )
        ).await()
    }

    override suspend fun install(vararg packages: String) {
        refresh()
        exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        id,
                        "zypper",
                        "--non-interactive",
                        "install",
                        *packages
                )
        ).await()
    }
}

object OpenSuseLeap15_2 : ZypperBasedDistribution(
        id = "openSUSE-Leap-15.2",
        distributionName = "openSUSE Leap",
        version = SemVer("15.2.0", jsObject<Options>()),
        productId = "9mzd0n9z4m4h",
        installerFile = "openSUSE-Leap-15.2.exe"
)

abstract class ApkBasedDistribution : Distribution {
    constructor(
            id: String,
            distributionName: String,
            version: SemVer,
            downloadUrl: URL,
            installerFile: String
    ) : super(id, distributionName, version, downloadUrl, installerFile)

    constructor(
            id: String,
            distributionName: String,
            version: SemVer,
            productId: String,
            installerFile: String
    ) : super(id, distributionName, version, productId, installerFile)

    private suspend fun refresh() {
        exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        id,
                        "apk",
                        "update"
                )
        ).await()
    }

    override suspend fun update() {
        refresh()
        exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        id,
                        "apk",
                        "upgrade"
                )
        ).await()
    }

    override suspend fun install(vararg packages: String) {
        refresh()
        exec(
                "wsl",
                arrayOf(
                        "--distribution",
                        id,
                        "apk",
                        "add",
                        *packages
                )
        ).await()
    }
}

object Alpine : ApkBasedDistribution(
        id = "Alpine",
        distributionName = "Alpine",
        version = SemVer("1.0.3", jsObject<Options>()),
        productId = "9p804crf0395",
        installerFile = "Alpine.exe"
)
