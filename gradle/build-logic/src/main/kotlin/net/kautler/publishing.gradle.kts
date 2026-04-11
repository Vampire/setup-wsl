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

import net.kautler.util.BuildFeaturesProvider
import net.kautler.util.InitJGit
import net.kautler.util.ProblemsProvider
import net.kautler.util.Property.Companion.boolean
import net.kautler.util.Property.Companion.optionalString
import net.kautler.util.ReleaseBody
import net.kautler.util.afterReleaseBuild
import net.kautler.util.beforeReleaseBuild
import net.kautler.util.cachedProvider
import net.kautler.util.checkoutMergeFromReleaseBranch
import net.kautler.util.checkoutMergeToReleaseBranch
import net.kautler.util.createReleaseTag
import net.kautler.util.preTagCommit
import net.kautler.util.registerMockTask
import net.kautler.util.release
import net.kautler.util.runBuildTasks
import net.kautler.util.updateVersion
import net.researchgate.release.ReleaseExtension
import net.researchgate.release.ReleasePlugin
import net.researchgate.release.tasks.CreateReleaseTag
import net.researchgate.release.tasks.PreTagCommit
import net.researchgate.release.tasks.UpdateVersion
import org.ajoberstar.grgit.operation.BranchListOp.Mode.ALL
import org.gradle.tooling.GradleConnector
import org.kohsuke.github.GHIssueState.OPEN
import wooga.gradle.github.base.tasks.Github
import wooga.gradle.github.base.tasks.internal.AbstractGithubTask
import wooga.gradle.github.publish.PublishMethod.update
import wooga.gradle.github.publish.tasks.GithubPublish
import java.net.URI

plugins {
    // needed for accessing majorVersion
    id("net.kautler.dependencies")
    id("org.ajoberstar.grgit.service")
    id("net.wooga.github")
    id("net.kautler.readme")
}

// part of work-around for https://github.com/researchgate/gradle-release/pull/405
if (objects.newInstance<BuildFeaturesProvider>().buildFeatures.configurationCache.active.get()) {
    extensions.create<ReleaseExtension>("release", project, emptyMap<String, Any>())
    tasks.registerMockTask<GradleBuild>("release")
    tasks.registerMockTask<GradleBuild>("runBuildTasks")
    tasks.registerMockTask<Task>("checkoutMergeFromReleaseBranch")
    tasks.registerMockTask<Task>("checkoutMergeToReleaseBranch")
    tasks.registerMockTask<UpdateVersion>("updateVersion")
    tasks.registerMockTask<Task>("afterReleaseBuild")
    tasks.registerMockTask<PreTagCommit>("preTagCommit")
    tasks.registerMockTask<CreateReleaseTag>("createReleaseTag")
    tasks.registerMockTask<Task>("beforeReleaseBuild")
} else {
    // part of work-around for https://github.com/researchgate/gradle-release/issues/304
    apply(plugin = "net.researchgate.release")
}

extra["release.useAutomaticVersion"] = boolean("release.useAutomaticVersion").getValue()
extra["release.releaseVersion"] = optionalString("release.releaseVersion").getValue()
extra["release.newVersion"] = optionalString("release.newVersion").getValue()

val majorVersion: String by project

release {
    pushReleaseVersionBranch = "v$majorVersion"
    tagTemplate = "v\$version"
    git {
        requireBranch = "master"
        signTag = true
    }
}

val releaseTagName = cachedProvider {
    plugins.findPlugin(ReleasePlugin::class)!!.tagName()
}

val releaseVersion get() = !"$version".endsWith("-SNAPSHOT")

val removeDistributionsFromGit by tasks.registering {
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

val gitHubToken by optionalString("github.token")

github {
    token = provider { gitHubToken }
}

// part of work-around for https://github.com/researchgate/gradle-release/issues/304
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
                        .addArguments("--no-configuration-cache")
                    gradle.startParameter.excludedTaskNames.forEach {
                        buildLauncher.addArguments("-x", it)
                    }
                    buildLauncher.run()
                }
        }
    }
}

// work-around for GitHub plugin using JGit without a ValueSource
// in non-configurable property branchName
providers.of(InitJGit::class) {
    parameters {
        projectDirectory = layout.projectDirectory
    }
}.get()

tasks.withType<GithubPublish>().configureEach {
    onlyIf { releaseVersion }
    tagName = releaseTagName
    releaseName = releaseTagName
}

tasks.githubPublish {
    body = providers.of(ReleaseBody::class) {
        parameters {
            projectDirectory = layout.projectDirectory
            githubToken = github.token
            repositoryName = github.repositoryName
            branchName = release.git.requireBranch
        }
    }
    draft = true
}

val undraftGithubRelease by tasks.registering(GithubPublish::class) {
    mustRunAfter(tasks.createReleaseTag)
    publishMethod = update
}

val finishMilestone by tasks.registering(Github::class) {
    val releaseVersion = provider { releaseVersion }
    onlyIf { releaseVersion.get() }

    val releaseTagName = releaseTagName
    doLast("finish milestone") {
        repository.apply {
            listMilestones(OPEN)
                .find { it.title == "Next Version" }!!
                .apply {
                    title = releaseTagName.get()
                    close()
                }

            createMilestone("Next Version", null)
        }
    }
}

val addDistributionsToGit by tasks.registering {
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

val createMajorBranch by tasks.registering {
    val grgitService = grgitService.service
    usesService(grgitService)

    val majorVersion = majorVersion
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

val preprocessVerifyReleaseWorkflow by tasks.existing {
    mustRunAfter(createMajorBranch)
    doFirst("Refresh version listing") {
        URI("https://bindings.krzeminski.it/refresh/Vampire/setup-wsl___major/maven-metadata.xml")
            .toURL()
            .readBytes()
    }
}

tasks.preTagCommit {
    val updateReadme by tasks.existing
    dependsOn(updateReadme)
    dependsOn(createMajorBranch)
    dependsOn(preprocessVerifyReleaseWorkflow)
}

// it does not really depend on, but there is no other hook to call
// it where it is necessary yet, which might be changed by
// https://github.com/researchgate/gradle-release/issues/309
tasks.updateVersion {
    dependsOn(undraftGithubRelease)
}

val checkBranchProtectionCompatibility by tasks.registering(Github::class) {
    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    val branchName = release.git.requireBranch
    doLast {
        if (repository
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
                solution("gh api 'repos/{owner}/{repo}/branches/${branchName.get()}/protection/enforce_admins' -X DELETE")
                severity(Severity.ERROR)
            }
        }
    }
}

tasks.withType<AbstractGithubTask<*>>().configureEach {
    notCompatibleWithConfigurationCache("Plugin is unmaintained and contains not-serializable state in tasks")
}

tasks.beforeReleaseBuild {
    dependsOn(checkBranchProtectionCompatibility)
}
