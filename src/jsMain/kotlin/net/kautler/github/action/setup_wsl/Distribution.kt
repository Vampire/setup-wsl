/*
 * Copyright 2020-2026 Björn Kautler
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

import actions.exec.ExecOptions
import actions.exec.exec
import js.objects.recordOf
import net.kautler.github.action.setup_wsl.InstallMethod.APP_BUNDLE
import net.kautler.github.action.setup_wsl.InstallMethod.TARBALL
import org.w3c.dom.url.URL
import semver.SemVer

val distributions = listOf(
    Alpine,
    Alpine317,
    Debian,
    Debian11,
    Kali,
    OpenSuseLeap15_2,
    Ubuntu1604,
    Ubuntu1804,
    Ubuntu2004,
    Ubuntu2204,
    Ubuntu2404
).associateBy { it.userId }

enum class InstallMethod { APP_BUNDLE, TARBALL }

sealed class Distribution(
    val wslId: String,
    val userId: String,
    val distributionName: String,
    val version: SemVer,
    val downloadUrl: URL,
    val installerFile: String? = null
) {
    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : this(wslId, wslId, distributionName, version, downloadUrl, installerFile)

    val downloadFileName = downloadUrl
        .pathname
        .substringAfterLast('/')
        .substringBefore('?')

    val installMethod = when {
        installerFile != null -> APP_BUNDLE
        downloadFileName.endsWith(".tar.gz") || downloadFileName.endsWith(".tgz") -> TARBALL
        else -> error("Unknown install method for download URL '$downloadUrl'")
    }

    open suspend fun createUser(username: String) {
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "useradd",
                "-m",
                "-p",
                "4qBD5NWD3IkbU",
                username
            )
        )
    }

    abstract suspend fun update()

    abstract suspend fun install(vararg packages: String)
}

abstract class AptGetBasedDistribution : Distribution {
    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, userId, distributionName, version, downloadUrl, installerFile)

    protected open suspend fun refresh() {
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "apt-get",
                "update"
            ),
            options = ExecOptions(
                env = recordOf(
                    "DEBIAN_FRONTEND" to "noninteractive",
                    "WSLENV" to "DEBIAN_FRONTEND/u"
                )
            )
        )
    }

    override suspend fun update() {
        update(true)
    }

    protected suspend fun update(refresh: Boolean) {
        if (refresh) {
            refresh()
        }
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "apt-get",
                "upgrade",
                "--yes"
            ),
            options = ExecOptions(
                env = recordOf(
                    "DEBIAN_FRONTEND" to "noninteractive",
                    "WSLENV" to "DEBIAN_FRONTEND/u"
                )
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
                "apt-get",
                "install",
                "--yes",
                "--no-install-recommends",
                *packages
            ),
            options = ExecOptions(
                env = recordOf(
                    "DEBIAN_FRONTEND" to "noninteractive",
                    "WSLENV" to "DEBIAN_FRONTEND/u"
                )
            )
        )
    }
}

object Ubuntu2404 : AptGetBasedDistribution(
    wslId = "Ubuntu-24.04",
    distributionName = "Ubuntu",
    version = SemVer("24.4.0"),
    downloadUrl = URL("https://wslstorestorage.blob.core.windows.net/wslblob/Ubuntu2404-240425.AppxBundle"),
    installerFile = "ubuntu2404.exe"
)

object Ubuntu2204 : AptGetBasedDistribution(
    wslId = "Ubuntu",
    userId = "Ubuntu-22.04",
    distributionName = "Ubuntu",
    version = SemVer("22.4.0"),
    downloadUrl = URL("https://aka.ms/wslubuntu2204"),
    installerFile = "ubuntu.exe"
)

object Ubuntu2004 : AptGetBasedDistribution(
    wslId = "Ubuntu",
    userId = "Ubuntu-20.04",
    distributionName = "Ubuntu",
    version = SemVer("20.4.0"),
    downloadUrl = URL("https://aka.ms/wslubuntu2004"),
    installerFile = "ubuntu.exe"
)

object Ubuntu1804 : AptGetBasedDistribution(
    wslId = "Ubuntu-18.04",
    distributionName = "Ubuntu",
    version = SemVer("18.4.0"),
    downloadUrl = URL("https://aka.ms/wsl-ubuntu-1804"),
    installerFile = "ubuntu1804.exe"
)

object Ubuntu1604 : AptGetBasedDistribution(
    wslId = "Ubuntu-16.04",
    distributionName = "Ubuntu",
    version = SemVer("16.4.0"),
    downloadUrl = URL("https://aka.ms/wsl-ubuntu-1604"),
    installerFile = "ubuntu1604.exe"
)

abstract class DebianDistribution : AptGetBasedDistribution {
    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, userId, distributionName, version, downloadUrl, installerFile)

    override suspend fun update() {
        refresh()
        retry(5) {
            update(false)
        }
    }
}

abstract class ArchivedDebianDistribution : DebianDistribution {
    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, userId, distributionName, version, downloadUrl, installerFile)

    override suspend fun refresh() {
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "sed",
                "-i",
                "s/ftp.debian.org/archive.debian.org/",
                "/etc/apt/sources.list"
            )
        )
        super.refresh()
    }
}

object Debian : ArchivedDebianDistribution(
    wslId = "Debian",
    distributionName = "Debian",
    version = SemVer("1.0.0"),
    downloadUrl = URL("https://aka.ms/wsl-debian-gnulinux"),
    installerFile = "debian.exe"
)

object Debian11 : ArchivedDebianDistribution(
    wslId = "Debian",
    userId = "Debian-11",
    distributionName = "Debian",
    version = SemVer("1.0.0"),
    downloadUrl = URL("https://aka.ms/wsl-debian-gnulinux"),
    installerFile = "debian.exe"
)

object Kali : AptGetBasedDistribution(
    wslId = "MyDistribution",
    userId = "kali-linux",
    distributionName = "Kali",
    version = SemVer("1.0.0"),
    downloadUrl = URL("https://aka.ms/wsl-kali-linux-new"),
    installerFile = "kali.exe"
) {
    override suspend fun refresh() {
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "wget",
                "https://archive.kali.org/archive-key.asc",
                "-O",
                "/etc/apt/trusted.gpg.d/kali-archive-keyring.asc"
            ),
            options = ExecOptions(
                env = recordOf(
                    "DEBIAN_FRONTEND" to "noninteractive",
                    "WSLENV" to "DEBIAN_FRONTEND/u"
                )
            )
        )
        super.refresh()
    }
}

abstract class ZypperBasedDistribution : Distribution {
    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, userId, distributionName, version, downloadUrl, installerFile)

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
    version = SemVer("15.2.0"),
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
        installerFile: String? = null
    ) : super(wslId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, userId, distributionName, version, downloadUrl, installerFile)

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

abstract class AlpineDistribution : ApkBasedDistribution {
    constructor(
        wslId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, distributionName, version, downloadUrl, installerFile)

    constructor(
        wslId: String,
        userId: String,
        distributionName: String,
        version: SemVer,
        downloadUrl: URL,
        installerFile: String? = null
    ) : super(wslId, userId, distributionName, version, downloadUrl, installerFile)

    override suspend fun createUser(username: String) {
        // Alpine minirootfs does not include the shadow package, so useradd is not available.
        // Use BusyBox adduser instead.
        exec(
            commandLine = "wsl",
            args = arrayOf(
                "--distribution",
                wslId,
                "adduser",
                "-D",
                username
            )
        )
    }
}

object Alpine : AlpineDistribution(
    wslId = "Alpine",
    distributionName = "Alpine",
    version = SemVer("3.17.10"),
    downloadUrl = URL("https://dl-cdn.alpinelinux.org/alpine/v3.17/releases/x86_64/alpine-minirootfs-3.17.10-x86_64.tar.gz")
)

object Alpine317 : AlpineDistribution(
    wslId = "Alpine",
    userId = "Alpine-3.17",
    distributionName = "Alpine",
    version = SemVer("3.17.10"),
    downloadUrl = URL("https://dl-cdn.alpinelinux.org/alpine/v3.17/releases/x86_64/alpine-minirootfs-3.17.10-x86_64.tar.gz")
)
