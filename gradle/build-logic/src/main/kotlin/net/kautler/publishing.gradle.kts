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

package net.kautler

import net.kautler.util.ProblemsProvider
import net.kautler.util.Property.Companion.boolean
import net.kautler.util.Property.Companion.optionalString
import net.kautler.util.ReleaseBody
import net.kautler.util.RepositoryName
import net.kautler.util.afterReleaseBuild
import net.kautler.util.beforeReleaseBuild
import net.kautler.util.checkoutMergeFromReleaseBranch
import net.kautler.util.checkoutMergeToReleaseBranch
import net.kautler.util.createReleaseTag
import net.kautler.util.preTagCommit
import net.kautler.util.release
import net.kautler.util.runBuildTasks
import net.kautler.util.updateVersion
import org.ajoberstar.grgit.operation.BranchListOp.Mode.ALL
import org.gradle.tooling.GradleConnector
import org.kohsuke.github.GHIssueState.OPEN
import org.kohsuke.github.GitHubBuilder
import java.net.URI
import kotlin.text.Charsets.ISO_8859_1

plugins {
    // needed for accessing majorVersion
    id("net.kautler.dependencies")
    id("org.ajoberstar.grgit.service")
    id("net.kautler.readme")
    publishing
}

// part of work-around for https://github.com/researchgate/gradle-release/issues/304
apply(plugin = "net.researchgate.release")

extra["release.useAutomaticVersion"] = boolean("release.useAutomaticVersion").getValue()
extra["release.releaseVersion"] = optionalString("release.releaseVersion").getValue()
extra["release.newVersion"] = optionalString("release.newVersion").getValue()

release {
    versionPropertyFileEncoding = ISO_8859_1.name()
    pushReleaseVersionBranch = "v${project.property("majorVersion")}"
    tagTemplate = "v\$version"
    git {
        requireBranch = "master"
        signTag = true
    }
}

internal val releaseTagName = tasks.createReleaseTag.flatMap { provider { it.tagName() } }

internal val releaseVersion get() = !"$version".endsWith("-SNAPSHOT")

internal val removeDistributionsFromGit = tasks.register("removeDistributionsFromGit") {
    mustRunAfter(tasks.checkoutMergeFromReleaseBranch)

    val grgitService = grgitService.service
    usesService(grgitService)

    doLast("Remove distributions from Git") {
        grgitService
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

// part of work-around for https://github.com/researchgate/gradle-release/issues/304
configure(listOf(tasks.release, tasks.runBuildTasks)) {
    configure {
        actions.clear()
        var projectDirectory = layout.projectDirectory.asFile
        var excludedTaskNames = gradle.startParameter.excludedTaskNames
        doLast {
            GradleConnector
                .newConnector()
                .forProjectDirectory(projectDirectory)
                .connect()
                .use { projectConnection ->
                    val buildLauncher = projectConnection
                        .newBuild()
                        .forTasks(*tasks.toTypedArray())
                        .setStandardInput(System.`in`)
                        .setStandardOutput(System.out)
                        .setStandardError(System.err)
                        .addArguments("--no-configuration-cache")
                        .addArguments("--no-isolated-projects")
                    excludedTaskNames.forEach {
                        buildLauncher.addArguments("-x", it)
                    }
                    buildLauncher.run()
                }
        }
    }
}

internal val githubToken by optionalString("github.token")
internal val githubRepositoryName = providers.of(RepositoryName::class) {
    parameters {
        explictGithubRepositoryName = optionalString("github.repositoryName").getValue()
        projectDirectory = layout.projectDirectory
    }
}

internal val githubPublish = tasks.register("githubPublish") {
    onlyIf {
        releaseVersion
    }

    doLast("Create draft GitHub release") {
        GitHubBuilder()
            .withOAuthToken(githubToken)
            .build()
            .getRepository(githubRepositoryName.get())
            .createRelease(releaseTagName.get())
            .name(releaseTagName.get())
            .body(providers.of(ReleaseBody::class) {
                val githubToken = githubToken
                parameters {
                    projectDirectory = layout.projectDirectory
                    this.githubToken = githubToken
                    repositoryName = githubRepositoryName
                    branchName = release.git.requireBranch
                }
            }.get())
            .draft(true)
            .create()
    }
}

internal val undraftGithubRelease = tasks.register("undraftGithubRelease") {
    mustRunAfter(tasks.createReleaseTag)

    onlyIf {
        releaseVersion
    }

    doLast("Unraft GitHub release") {
        GitHubBuilder()
            .withOAuthToken(githubToken)
            .build()
            .getRepository(githubRepositoryName.get())
            .listReleases()
            .find { it.tagName == releaseTagName.get() }!!
            .update()
            .draft(false)
            .update()
    }
}

internal val finishMilestone = tasks.register("finishMilestone") {
    onlyIf {
        releaseVersion
    }

    val releaseTagName = releaseTagName
    doLast("Finish milestone") {
        var repository = GitHubBuilder()
            .withOAuthToken(githubToken)
            .build()
            .getRepository(githubRepositoryName.get())

        repository
            .listMilestones(OPEN)
            .find { it.title == "Next Version" }!!
            .apply {
                title = releaseTagName.get()
                close()
            }

        repository
            .createMilestone("Next Version", null)
    }
}

internal val addDistributionsToGit = tasks.register("addDistributionsToGit") {
    dependsOn(tasks.named("assemble"))

    val grgitService = grgitService.service
    usesService(grgitService)

    doLast("Add distributions to Git") {
        val gitIgnore = file(".gitignore")
        val gitIgnoreBak = file(".gitignore.bak")
        gitIgnore.renameTo(gitIgnoreBak)
        try {
            grgitService
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
    dependsOn(githubPublish)
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

internal val createMajorBranch = tasks.register("createMajorBranch") {
    val grgitService = grgitService.service
    usesService(grgitService)

    val majorVersion = project.property("majorVersion") as String
    doLast {
        val grgit = grgitService.get().grgit

        val (maxMajorVersion, maxMajorVersionBranch) = grgit
            .branch
            .list { mode = ALL }
            .filter {
                it.name.matches("""(origin/)?v\d+""".toRegex())
            }
            .map { it.name.substringAfter('/').drop(1).toInt() to it }
            .maxBy { it.first }

        if (maxMajorVersion < majorVersion.toInt()) {
            grgit.push { refsOrSpecs = listOf("${maxMajorVersionBranch.name}:refs/heads/v$majorVersion") }
        }
    }
}

tasks.checkoutMergeToReleaseBranch {
    dependsOn(createMajorBranch)
}

internal val preprocessVerifyReleaseWorkflow = tasks.named("preprocessVerifyReleaseWorkflow") {
    mustRunAfter(createMajorBranch)
    doFirst("Refresh version listing") {
        URI("https://bindings.krzeminski.it/refresh/Vampire/setup-wsl___major/maven-metadata.xml")
            .toURL()
            .readBytes()
    }
}

tasks.preTagCommit {
    dependsOn(tasks.named("updateReadme"))
    dependsOn(createMajorBranch)
    dependsOn(preprocessVerifyReleaseWorkflow)
}

// it does not really depend on, but there is no other hook to call
// it where it is necessary yet, which might be changed by
// https://github.com/researchgate/gradle-release/issues/309
tasks.updateVersion {
    dependsOn(undraftGithubRelease)
}

internal val checkBranchProtectionCompatibility = tasks.register("checkBranchProtectionCompatibility") {
    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    val branchName = release.git.requireBranch
    doLast {
        if (GitHubBuilder()
                .withOAuthToken(githubToken)
                .build()
                .getRepository(githubRepositoryName.get())
                .getBranch(branchName.get())
                .protection
                .enforceAdmins
                .isEnabled
        ) {
            throw problemReporter.throwing(
                IllegalStateException(),
                ProblemId.create(
                    "the-branch-protection-for-administrators-is-active",
                    "The branch protection for administrators is active",
                    ProblemGroup.create("remote-state", "Remote State")
                )
            ) {
                solution("Disable branch protection for administrators before triggering a release")
                solution("""Execute: "gh api 'repos/{owner}/{repo}/branches/${branchName.get()}/protection/enforce_admins' -X DELETE"""")
            }
        }
    }
}

tasks.beforeReleaseBuild {
    dependsOn(checkBranchProtectionCompatibility)
}
