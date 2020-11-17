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

package net.kautler

import net.kautler.util.Property.Companion.boolean
import net.kautler.util.Property.Companion.optionalString
import net.kautler.util.git
import net.researchgate.release.ReleasePlugin
import org.kohsuke.github.GHIssueState.OPEN
import org.kohsuke.github.GitHub
import wooga.gradle.github.publish.PublishMethod.update
import wooga.gradle.github.publish.tasks.GithubPublish
import java.awt.GraphicsEnvironment.isHeadless
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JOptionPane.DEFAULT_OPTION
import javax.swing.JOptionPane.OK_OPTION
import javax.swing.JOptionPane.QUESTION_MESSAGE
import javax.swing.JOptionPane.showOptionDialog
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.LazyThreadSafetyMode.NONE

plugins {
    id("net.researchgate.release")
    id("org.ajoberstar.grgit")
    id("net.wooga.github")
}

extra["release.useAutomaticVersion"] = boolean(project, "release.useAutomaticVersion").getValue()
extra["release.releaseVersion"] = optionalString(project, "release.releaseVersion").getValue()
extra["release.newVersion"] = optionalString(project, "release.newVersion").getValue()
extra["github.token"] = optionalString(project, "github.token").getValue()

val majorVersion: String by project

release {
    pushReleaseVersionBranch = "v$majorVersion"
    tagTemplate = "v\$version"
    git {
        signTag = true
    }
}

val githubRepositoryName by lazy(NONE) {
    grgit
            .remote
            .list()
            .find { it.name == "origin" }
            ?.let { remote ->
                Regex("""(?x)
                    (?:
                        ://([^@]++@)?+github\.com(?::\d++)?+/ |
                        ([^@]++@)?+github\.com:
                    )
                    (?<repositoryName>.*)
                    \.git
                """)
                        .find(remote.url)
                        ?.let { it.groups["repositoryName"]!!.value }
            } ?: "Vampire/setup-wsl"
}

val releasePlugin by lazy(NONE) {
    plugins.findPlugin(ReleasePlugin::class)!!
}

val releaseTagName by lazy(NONE) {
    releasePlugin.tagName()!!
}

val github by lazy(NONE) {
    GitHub.connectUsingOAuth(extra["github.token"] as String)!!
}

val releaseBody by lazy(NONE) {
    val releaseBody = grgit.log {
        github.getRepository(githubRepositoryName).latestRelease?.apply { excludes.add(tagName) }
    }.filter { commit ->
        !commit.shortMessage.startsWith("[Gradle Release Plugin] ")
    }.joinToString("\n") { commit ->
        "- ${commit.shortMessage} [${commit.id}]"
    }

    if (isHeadless()) {
        return@lazy releaseBody
    }

    val result = CompletableFuture<String>()

    SwingUtilities.invokeLater {
        val initialReleaseBody = """
            # Highlights
            -

            # Details

        """.trimIndent() + releaseBody

        val textArea = JTextArea(initialReleaseBody)

        val parentFrame = JFrame().apply {
            isUndecorated = true
            setLocationRelativeTo(null)
            isVisible = true
        }

        val resetButton = JButton("Reset").apply {
            addActionListener {
                textArea.text = initialReleaseBody
            }
        }

        result.complete(try {
            when (showOptionDialog(
                    parentFrame, JScrollPane(textArea), "Release Body",
                    DEFAULT_OPTION, QUESTION_MESSAGE, null,
                    arrayOf("OK", resetButton), null
            )) {
                OK_OPTION -> textArea.text!!
                else -> releaseBody
            }
        } finally {
            parentFrame.dispose()
        })
    }

    result.join()!!
}

val releaseVersion = !"$version".endsWith("-SNAPSHOT")

val removeDistributionsFromGit by tasks.registering {
    mustRunAfter(tasks.checkoutMergeFromReleaseBranch)

    doLast("Remove distributions from Git") {
        grgit.remove {
            cached = true
            patterns = setOf("build/distributions")
        }
    }
}
tasks.updateVersion {
    dependsOn(removeDistributionsFromGit)
}

tasks.withType<GithubPublish>().configureEach {
    enabled = releaseVersion
    repositoryName(githubRepositoryName)
    tagName(Callable { releaseTagName })
    releaseName(Callable { releaseTagName })
}

tasks.githubPublish {
    body { releaseBody }
    draft(true)
}

val configureUndraftGithubRelease by tasks.registering

val undraftGithubRelease by tasks.registering(GithubPublish::class) {
    dependsOn(configureUndraftGithubRelease)

    publishMethod = update
}

configureUndraftGithubRelease {
    doLast {
        undraftGithubRelease {
            enabled = !"$version".endsWith("-SNAPSHOT")
        }
    }
}

val finishMilestone by tasks.registering {
    enabled = releaseVersion

    doLast("finish milestone") {
        github.getRepository(githubRepositoryName)!!.run {
            listMilestones(OPEN)
                    .find { it.title == "Next Version" }!!
                    .run {
                        title = releaseTagName
                        close()
                    }

            createMilestone("Next Version", null)
        }
    }
}

val addDistributionsToGit by tasks.registering {
    dependsOn(tasks.named("assemble"))

    doLast("Add distributions to Git") {
        val gitIgnore = file(".gitignore")
        val gitIgnoreBak = file(".gitignore.bak")
        gitIgnore.renameTo(gitIgnoreBak)
        try {
            grgit.add {
                patterns = setOf("build/distributions")
            }
        } finally {
            gitIgnoreBak.renameTo(gitIgnore)
        }
    }
}
tasks.publish {
    dependsOn(addDistributionsToGit)
}

tasks.afterReleaseBuild {
    dependsOn(tasks.publish)
}

// those must not run before publish was run
listOf(finishMilestone).forEach {
    it.configure {
        mustRunAfter(tasks.publish)
    }
    tasks.afterReleaseBuild {
        dependsOn(it)
    }
}

tasks.preTagCommit {
    dependsOn(tasks.named("updateReadme"))
}

undraftGithubRelease {
    mustRunAfter(tasks.createReleaseTag)
}

// it does not really depend on, but there is no other hook to call
// it where it is necessary yet, which might be changed by
// https://github.com/researchgate/gradle-release/issues/309
tasks.updateVersion {
    dependsOn(undraftGithubRelease)
}

tasks.commitNewVersion {
    doFirst {
        release {
            git {
                pushToBranchPrefix = "test/"
            }
        }
    }
    doLast {
        grgit.push {
            refsOrSpecs = listOf(":refs/heads/test/master")
        }
    }
}
