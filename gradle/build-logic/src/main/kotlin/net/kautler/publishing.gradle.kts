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

package net.kautler

import net.kautler.util.Property.Companion.boolean
import net.kautler.util.Property.Companion.optionalString
import net.kautler.util.afterReleaseBuild
import net.kautler.util.beforeReleaseBuild
import net.kautler.util.checkoutMergeFromReleaseBranch
import net.kautler.util.createReleaseTag
import net.kautler.util.preTagCommit
import net.kautler.util.release
import net.kautler.util.runBuildTasks
import net.kautler.util.updateVersion
import net.researchgate.release.ReleasePlugin
import org.gradle.tooling.GradleConnector
import org.kohsuke.github.GHIssueState.OPEN
import wooga.gradle.github.base.tasks.Github
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
    // needed for accessing majorVersion
    id("net.kautler.dependencies")
    id("org.ajoberstar.grgit.service")
    id("net.wooga.github")
}

// part of work-around for https://github.com/gradle/gradle/issues/23747
apply(plugin = "net.researchgate.release")

extra["release.useAutomaticVersion"] = boolean(project, "release.useAutomaticVersion").getValue()
extra["release.releaseVersion"] = optionalString(project, "release.releaseVersion").getValue()
extra["release.newVersion"] = optionalString(project, "release.newVersion").getValue()

val majorVersion: String by project

release {
    pushReleaseVersionBranch.set("v$majorVersion")
    tagTemplate.set("v\$version")
    git {
        requireBranch.set("master")
        signTag.set(true)
    }
}

val releasePlugin by lazy(NONE) {
    plugins.findPlugin(ReleasePlugin::class)!!
}

val releaseTagName by lazy(NONE) {
    releasePlugin.tagName()!!
}

val releaseVersion get() = !"$version".endsWith("-SNAPSHOT")

val removeDistributionsFromGit by tasks.registering {
    mustRunAfter(tasks.checkoutMergeFromReleaseBranch)
    // work-around for https://github.com/ajoberstar/grgit/pull/382
    //usesService(grgitService.service)

    doLast("Remove distributions from Git") {
        grgitService
            .service
            .get()
            .grgit
            .remove {
                cached = true
                patterns = setOf("build/distributions")
            }
    }
}
tasks.updateVersion {
    dependsOn(removeDistributionsFromGit)
}

val gitHubToken by optionalString("github.token", project)

github {
    token.set(provider { gitHubToken })
}

configure(listOf(tasks.release, tasks.runBuildTasks)) {
    configure {
        actions.clear()
        doLast {
            GradleConnector
                .newConnector()
                .forProjectDirectory(layout.projectDirectory.asFile)
                .connect()
                .use { projectConnection ->
                    val buildLauncher = projectConnection
                        .newBuild()
                        .forTasks(*tasks.toTypedArray())
                        .setStandardInput(System.`in`)
                        .setStandardOutput(System.out)
                        .setStandardError(System.err)
                    gradle.startParameter.excludedTaskNames.forEach {
                        buildLauncher.addArguments("-x", it)
                    }
                    buildLauncher.run()
                }
        }
    }
}

tasks.withType<GithubPublish>().configureEach {
    onlyIf { releaseVersion }
    tagName.set(provider { releaseTagName })
    releaseName.set(provider { releaseTagName })
}

tasks.githubPublish {
    // work-around for https://github.com/ajoberstar/grgit/pull/382
    //usesService(grgitService.service)
    body.set(provider {
        val releaseBody = grgitService
            .service
            .get()
            .grgit
            .log {
                includes.add(release.git.requireBranch.get())
                github
                    .clientProvider
                    .get()
                    .getRepository(github.repositoryName.get())
                    .latestRelease
                    ?.apply {
                        excludes.add(tagName)
                    }
            }
            .filter { commit ->
                !commit.shortMessage.startsWith("[Gradle Release Plugin] ")
            }
            .asReversed()
            .joinToString("\n") { commit ->
                "- ${commit.shortMessage} [${commit.id}]"
            }

        if (isHeadless()) {
            return@provider releaseBody
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

            result.complete(
                try {
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
                }
            )
        }

        return@provider result.join()!!
    })
    draft.set(true)
}

val undraftGithubRelease by tasks.registering(GithubPublish::class) {
    publishMethod.set(update)
}

val finishMilestone by tasks.registering(Github::class) {
    enabled = releaseVersion
    // work-around for https://github.com/ajoberstar/grgit/pull/382
    //usesService(grgitService.service)

    doLast("finish milestone") {
        repository.apply {
            listMilestones(OPEN)
                .find { it.title == "Next Version" }!!
                .apply {
                    title = releaseTagName
                    close()
                }

            createMilestone("Next Version", null)
        }
    }
}

val addDistributionsToGit by tasks.registering {
    dependsOn(tasks.named("assemble"))
    // work-around for https://github.com/ajoberstar/grgit/pull/382
    //usesService(grgitService.service)

    doLast("Add distributions to Git") {
        val gitIgnore = file(".gitignore")
        val gitIgnoreBak = file(".gitignore.bak")
        gitIgnore.renameTo(gitIgnoreBak)
        try {
            grgitService
                .service
                .get()
                .grgit
                .add {
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

val checkBranchProtectionCompatibility by tasks.registering(Github::class) {
    // work-around for https://github.com/ajoberstar/grgit/pull/382
    //usesService(grgitService.service)

    doLast {
        check(
            !repository
                .getBranch(release.git.requireBranch.get())
                .protection
                .enforceAdmins
                .isEnabled
        ) {
            """
                Please disable branch protection for administrators before triggering a release, for example using

                gh api 'repos/{owner}/{repo}/branches/${release.git.requireBranch.get()}/protection/enforce_admins' -X DELETE
            """.trimIndent()
        }
    }
}

tasks.beforeReleaseBuild {
    dependsOn(checkBranchProtectionCompatibility)
}
