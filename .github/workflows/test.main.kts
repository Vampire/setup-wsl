#!/usr/bin/env kotlin

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

@file:Import("workflow-with-copyright.main.kts")

import io.github.typesafegithub.workflows.actions.actions.CacheRestoreV4
import io.github.typesafegithub.workflows.actions.actions.CacheSaveV4
import io.github.typesafegithub.workflows.actions.actions.CheckoutV4
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV4
import io.github.typesafegithub.workflows.actions.actions.SetupJavaV4.Distribution.Temurin
import io.github.typesafegithub.workflows.actions.burrunan.GradleCacheActionV1
import io.github.typesafegithub.workflows.actions.vampire.SetupWslV3
import io.github.typesafegithub.workflows.actions.vampire.SetupWslV3.Distribution
import io.github.typesafegithub.workflows.domain.CommandStep
import io.github.typesafegithub.workflows.domain.ActionStep
import io.github.typesafegithub.workflows.domain.JobOutputs.EMPTY
import io.github.typesafegithub.workflows.domain.AbstractResult.Status.Success
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.RunnerType.WindowsLatest
import io.github.typesafegithub.workflows.domain.Shell
import io.github.typesafegithub.workflows.domain.Shell.Cmd
import io.github.typesafegithub.workflows.domain.Step
import io.github.typesafegithub.workflows.domain.triggers.Cron
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.domain.triggers.Schedule
import io.github.typesafegithub.workflows.dsl.JobBuilder
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import io.github.typesafegithub.workflows.dsl.expressions.expr
import kotlin.math.min

val environments = listOf(
    "windows-2019",
    "windows-2022",
    "windows-latest"
)

val debian = mapOf(
    "wsl-id" to "Debian",
    "user-id" to "Debian",
    "match-pattern" to "*Debian*",
    "default-absent-tool" to "dos2unix"
)

val alpine = mapOf(
    "wsl-id" to "Alpine",
    "user-id" to "Alpine",
    "match-pattern" to "*Alpine*",
    "default-absent-tool" to "dos2unix"
)

val kali = mapOf(
    "wsl-id" to "kali-linux",
    "user-id" to "kali-linux",
    "match-pattern" to "*Kali*",
    "default-absent-tool" to "dos2unix"
)

val openSuseLeap15_2 = mapOf(
    "wsl-id" to "openSUSE-Leap-15.2",
    "user-id" to "openSUSE-Leap-15.2",
    "match-pattern" to "*openSUSE*Leap*15.2*",
    "default-absent-tool" to "which"
)

val ubuntu2404 = mapOf(
    "wsl-id" to "Ubuntu-24.04",
    "user-id" to "Ubuntu-24.04",
    "match-pattern" to "*Ubuntu*24.04*",
    "default-absent-tool" to "dos2unix"
)

val ubuntu2204 = mapOf(
    "wsl-id" to "Ubuntu",
    "user-id" to "Ubuntu-22.04",
    "match-pattern" to "*Ubuntu*22.04*",
    "default-absent-tool" to "dos2unix"
)

val ubuntu2004 = mapOf(
    "wsl-id" to "Ubuntu",
    "user-id" to "Ubuntu-20.04",
    "match-pattern" to "*Ubuntu*20.04*",
    "default-absent-tool" to "dos2unix"
)

val ubuntu1804 = mapOf(
    "wsl-id" to "Ubuntu-18.04",
    "user-id" to "Ubuntu-18.04",
    "match-pattern" to "*Ubuntu*18.04*",
    "default-absent-tool" to "dos2unix"
)

val ubuntu1604 = mapOf(
    "wsl-id" to "Ubuntu-16.04",
    "user-id" to "Ubuntu-16.04",
    "match-pattern" to "*Ubuntu*16.04*",
    "default-absent-tool" to "dos2unix"
)

val distributions = listOf(
    debian,
    alpine,
    kali,
    openSuseLeap15_2,
    ubuntu2404,
    ubuntu2204,
    ubuntu2004,
    ubuntu1804,
    ubuntu1604
)

val wslBash = Shell.Custom("wsl-bash {0}")

val wslSh = Shell.Custom("wsl-sh {0}")

lateinit var executeActionStep: ActionStep<SetupWslV3.Outputs>

workflowWithCopyright(
    name = "Build and Test",
    on = listOf(
        Push(),
        PullRequest(),
        Schedule(listOf(Cron(minute = "0", hour = "0")))
    ),
    sourceFile = __FILE__
) {
    val builtArtifacts = listOf(
        "action.yml",
        "build/distributions/"
    )

    val executeAction = SetupWslV3(
        distribution = Distribution.Custom(expr("matrix.distribution.user-id"))
    )

    val build = job(
        id = "build",
        name = "Build",
        runsOn = WindowsLatest
    ) {
        run(
            name = "Configure Git",
            command = "git config --global core.autocrlf input"
        )
        uses(
            name = "Checkout",
            action = CheckoutV4()
        )
        uses(
            name = "Setup Java 11",
            action = SetupJavaV4(
                javaVersion = "11",
                distribution = Temurin
            )
        )
        uses(
            name = "Build",
            action = GradleCacheActionV1(
                arguments = listOf(
                    "--show-version",
                    "build",
                    "--info",
                    "--stacktrace",
                    "--scan"
                ),
                debug = false,
                concurrent = true
            )
        )
        uses(
            name = "Save built artifacts to cache",
            action = CacheSaveV4(
                path = builtArtifacts,
                key = expr { github.run_id }
            )
        )
    }

    fun WorkflowBuilder.testJob(
        id: String,
        name: String,
        _customArguments: Map<String, Any?> = mapOf(),
        block: JobBuilder<EMPTY>.() -> Unit,
    ) = job(
        id = id,
        name = name,
        needs = listOf(build),
        runsOn = RunnerType.Custom(expr("matrix.environment")),
        _customArguments = _customArguments
    ) {
        uses(
            name = "Restore built artifacts from cache",
            action = CacheRestoreV4(
                path = builtArtifacts,
                key = expr { github.run_id },
                failOnCacheMiss = true
            )
        )
        block()
    }

    testJob(
        id = "test_invalid_distribution",
        name = """Test "${expr("matrix.distribution.label")}" distribution on ${expr("matrix.environment")}""",
        _customArguments = mapOf(
            "strategy" to mapOf(
                "fail-fast" to false,
                "matrix" to mapOf(
                    "environment" to environments,
                    "distribution" to listOf(
                        mapOf(
                            "user-id" to "invalid",
                            "label" to "invalid"
                        ),
                        mapOf(
                            "user-id" to "",
                            "label" to ""
                        ),
                        mapOf(
                            "user-id" to null,
                            "label" to "null"
                        )
                    )
                )
            )
        )
    ) {
        executeActionStep = usesSelf(
            action = executeAction,
            continueOnError = true
        )
        run(
            name = "Test - action should fail if an invalid distribution is given",
            shell = Cmd,
            command = "if '${expr("${executeActionStep.outcome}")}' NEQ 'failure' exit 1"
        )
    }

    testJob(
        id = "test_default_distribution",
        name = "Test default distribution on ${expr("matrix.environment")}",
        _customArguments = mapOf(
            "strategy" to mapOf(
                "fail-fast" to false,
                "matrix" to mapOf(
                    "environment" to environments,
                    "distribution" to listOf(debian)
                )
            )
        )
    ) {
        executeActionStep = usesSelf(
            action = SetupWslV3(
                update = true
            )
        )
        commonTests()
        verifyFailure(
            name = "Test - wsl-bash should fail if no script file is given",
            provocationShell = Shell.Custom("wsl-bash")
        )
        verifyFailure(
            name = "Test - wsl-bash should fail if more than one parameter is given and first is not -u",
            provocationShell = Shell.Custom("wsl-bash user {0}")
        )
        verifyFailure(
            name = "Test - wsl-bash should fail if only user is given",
            provocationShell = Shell.Custom("wsl-bash -u {0}")
        )
        verifyFailure(
            name = "Test - wsl-bash should fail if excess argument is given",
            provocationShell = Shell.Custom("wsl-bash -u user {0} foo")
        )
        verifyFailure(
            name = "Test - wsl-bash should fail if given script file does not exist",
            provocationShell = Shell.Custom("wsl-bash -u user {0}foo")
        )
    }

    testJob(
        id = "test",
        name = """Test "${expr("matrix.distribution.user-id")}" distribution on ${expr("matrix.environment")}""",
        _customArguments = mapOf(
            "strategy" to mapOf(
                "fail-fast" to false,
                "matrix" to mapOf(
                    "environment" to environments,
                    "distribution" to distributions
                )
            )
        )
    ) {
        executeActionStep = usesSelf(
            action = executeAction.copy(
                useCache = false
            )
        )
        verifyFailure(
            name = "Test - wsl-bash should fail if bash is not present by default",
            conditionTransformer = { executeActionStep.successOnAlpineCondition },
            verificationShell = null,
            verificationTransformer = { _, command ->
                """wsl sh -euc "${command.replace("==", "=")}""""
            }
        )
        deleteWslBashOnAlpine()
        usesSelf(
            name = "Install Bash on Alpine",
            action = executeAction.copy(
                additionalPackages = listOf("bash")
            ),
            condition = executeActionStep.successOnAlpineCondition
        )
        commonTests()
        verifyFailure(
            name = "Test - ${expr("matrix.distribution.default-absent-tool")} should not be installed by default",
            provocationCommand = "${expr("matrix.distribution.default-absent-tool")} --version"
        )
        runAfterSuccess(
            name = "Test - bash should be installed by default",
            command = "bash -c true"
        )
        runAfterSuccess(
            name = "Test - sh should be installed by default",
            command = "sh -c true"
        )
        verifyFailure(
            name = "Test - wsl-sh should not be present",
            provocationShell = wslSh
        )
        val wslBashPath = executeActionStep.outputs.wslShellWrapperPath
        executeActionStep = usesSelfAfterSuccess(
            name = "Add wsl-sh wrapper",
            action = executeAction.copy(
                wslShellCommand = "sh -eu"
            )
        )
        runAfterSuccess(
            name = "Test - wsl-sh should be present",
            shell = wslSh
        )
        runAfterSuccess(
            name = "Test - wsl-bash should use bash",
            command = """
                ps -o pid='' -o comm='' | grep "^\s\+$$\s\+" | grep -o '\S\+$'
                [ "$(ps -o pid='' -o comm='' 2>/dev/null | grep "^\s\+$$\s\+" | grep -o '\S\+$')" == 'bash' ]
            """
        )
        runAfterSuccess(
            name = "Test - wsl-sh should use sh",
            shell = wslSh,
            command = """
                ps -o pid='' -o comm='' | grep "^\s\+$$\s\+" | grep -o '\S\+$'
                [ "$(ps -o pid='' -o comm='' 2>/dev/null | grep "^\s\+$$\s\+" | grep -o '\S\+$')" = 'sh' ]
            """
        )
        deleteWslBash(
            wslBashPathExpression = wslBashPath
        )
        verifyFailure(
            name = "Test - wsl-bash should not be present",
            verificationShell = wslSh,
            verificationTransformer = { _, command ->
                command.replace("==", "=")
            }
        )
        executeActionStep = usesSelfAfterSuccess(
            name = "Re-add wsl-bash wrapper",
            action = executeAction
        )
        runAfterSuccess(
            name = "Test - wsl-bash should be present"
        )
        runAfterSuccess(
            name = "Test - wsl-bash should use bash",
            command = """
                ps -o pid='' -o comm='' | grep "^\s\+$$\s\+" | grep -o '\S\+$'
                [ "$(ps -o pid='' -o comm='' 2>/dev/null | grep "^\s\+$$\s\+" | grep -o '\S\+$')" == 'bash' ]
            """
        )
        verifyCommandResult(
            name = "Test - wsl-bash should use root as default user",
            actualCommand = "whoami",
            expected = "root"
        )
        runAfterSuccess(
            name = "Add user test",
            command = "useradd -m -p 4qBD5NWD3IkbU test"
        )
        executeActionStep = usesSelfAfterSuccess(
            name = "Set wsl-bash wrapper to use user test by default",
            action = executeAction.copy(
                additionalPackages = listOf("sudo"),
                wslShellCommand = """bash -c "sudo -u test bash --noprofile --norc -euo pipefail "\"""
            )
        )
        verifyCommandResult(
            name = "Test - wsl-bash should use test as default user",
            actualCommand = "whoami",
            expected = "test"
        )
        executeActionStep = usesSelfAfterSuccess(
            name = "Set wsl-bash wrapper to use user test by default with inline script usage",
            action = executeAction.copy(
                wslShellCommand = """bash -c "sudo -u test bash --noprofile --norc -euo pipefail '{0}'""""
            )
        )
        verifyCommandResult(
            name = "Test - wsl-bash should use test as default user with inline script usage",
            actualCommand = "whoami",
            expected = "test"
        )
        deleteWslBash()
        executeActionStep = usesSelfAfterSuccess(
            name = "Set wsl-bash wrapper to use default user by default",
            action = executeAction
        )
        verifyCommandResult(
            name = "Test - wsl-bash should use root as default user",
            actualCommand = "whoami",
            expected = "root"
        )
        runAfterSuccess(
            name = "Test - test user does already exist",
            command = "id -u test"
        )
        deleteWslBash()
        executeActionStep = usesSelfAfterSuccess(
            name = "Set wsl-bash wrapper to use existing user test by default with extra parameter",
            action = executeAction.copy(
                wslShellUser = "test"
            )
        )
        verifyCommandResult(
            name = "Test - wsl-bash should use existing user test as default user with extra parameter",
            actualCommand = "whoami",
            expected = "test"
        )
        runAfterSuccess(
            name = "Test - test2 user does not exist",
            command = "! id -u test2"
        )
        deleteWslBash()
        executeActionStep = usesSelfAfterSuccess(
            name = "Set wsl-bash wrapper to use non-existing user test2 by default with extra parameter",
            action = executeAction.copy(
                wslShellUser = "test2"
            )
        )
        verifyCommandResult(
            name = "Test - wsl-bash should use auto-generated user test2 as default user",
            actualCommand = "whoami",
            expected = "test2"
        )
        verifyCommandResult(
            name = "Test - wsl-bash should use ad-hoc user test",
            actualCommand = "whoami",
            shell = Shell.Custom("wsl-bash -u test {0}"),
            expected = "test"
        )
        verifyCommandResult(
            name = "Test - wsl-bash should use ad-hoc user root",
            actualCommand = "whoami",
            shell = Shell.Custom("wsl-bash -u root {0}"),
            expected = "root"
        )
        executeActionStep = usesSelfAfterSuccess(
            name = "Make a no-op execution of the action",
            action = executeAction
        )
        verifyCommandResult(
            name = "Test - wsl-bash should still use test2 as default user",
            actualCommand = "whoami",
            expected = "test2"
        )
    }

    testJob(
        id = "test_wsl-conf_on_initial_execution",
        name = """Test /etc/wsl.conf handling on initial execution for "${expr("matrix.distribution.user-id")}" distribution on ${expr("matrix.environment")}""",
        _customArguments = mapOf(
            "strategy" to mapOf(
                "fail-fast" to false,
                "matrix" to mapOf(
                    "environment" to environments,
                    "distribution" to distributions
                )
            )
        )
    ) {
        executeActionStep = usesSelf(
            action = executeAction.copy(
                wslConf = """
                    [automount]
                    options = uid=1000
                """.trimIndent()
            )
        )
        deleteWslBashOnAlpine()
        usesSelf(
            name = "Install Bash on Alpine",
            action = executeAction.copy(
                additionalPackages = listOf("bash")
            ),
            condition = executeActionStep.successOnAlpineCondition
        )
        runAfterSuccess(
            name = "Test - /etc/wsl.conf should exist",
            command = """
                [ -f /etc/wsl.conf ]
                cat /etc/wsl.conf
            """
        )
        runAfterSuccess(
            name = "Test - /mnt/c should be mounted with uid 1000",
            command = """
                ls -alh /mnt
                [[ "$(stat -c %u /mnt/c)" == 1000 ]]
            """
        )
    }

    testJob(
        id = "test_wsl-conf_on_subsequent_execution",
        name = """Test /etc/wsl.conf handling on subsequent execution for "${expr("matrix.distribution.user-id")}" distribution on ${expr("matrix.environment")}""",
        _customArguments = mapOf(
            "strategy" to mapOf(
                "fail-fast" to false,
                "matrix" to mapOf(
                    "environment" to environments,
                    "distribution" to distributions
                )
            )
        )
    ) {
        executeActionStep = usesSelf(
            action = executeAction
        )
        deleteWslBashOnAlpine()
        usesSelf(
            name = "Install Bash on Alpine",
            action = executeAction.copy(
                additionalPackages = listOf("bash")
            ),
            condition = executeActionStep.successOnAlpineCondition
        )
        runAfterSuccess(
            name = "Test - /etc/wsl.conf should not exist",
            command = "[ ! -f /etc/wsl.conf ] || { cat /etc/wsl.conf; false; }",
            conditionTransformer = { executeActionStep.successNotOnUbuntu2404Condition }
        )
        runAfterSuccess(
            name = "Test - C: should be mounted at /mnt/c",
            command = """
                mount
                mount | grep 'C:.* on /mnt/c'
            """
        )
        runAfterSuccess(
            name = "Test - /mnt/c should be mounted with uid 0",
            command = """
                ls -alh /mnt
                [[ "$(stat -c %u /mnt/c)" == 0 ]]
            """
        )
        executeActionStep = usesSelfAfterSuccess(
            action = executeAction.copy(
                wslConf = """
                    [automount]
                    root = /
                """.trimIndent()
            )
        )
        runAfterSuccess(
            name = "Test - /etc/wsl.conf should exist",
            command = """
                [ -f /etc/wsl.conf ]
                cat /etc/wsl.conf
            """
        )
        runAfterSuccess(
            name = "Test - C: should be mounted at /c",
            command = """
                mount
                mount | grep 'C:.* on /c'
            """
        )
    }

    testJob(
        id = "test_additional_packages",
        name = """Test additional packages for "${expr("matrix.distribution.user-id")}" distribution on ${expr("matrix.environment")}""",
        _customArguments = mapOf(
            "strategy" to mapOf(
                "fail-fast" to false,
                "matrix" to mapOf(
                    "environment" to environments,
                    "distribution" to distributions
                )
            )
        )
    ) {
        executeActionStep = usesSelf(
            action = executeAction.copy(
                additionalPackages = listOf(
                    expr("matrix.distribution.default-absent-tool"),
                    "bash"
                )
            )
        )
        runAfterSuccess(
            name = "Test - ${expr("matrix.distribution.default-absent-tool")} should be installed",
            command = "${expr("matrix.distribution.default-absent-tool")} --version"
        )
        runAfterSuccess(
            name = "Test - bash should be installed",
            command = "bash -c true"
        )
    }

    testJob(
        id = "test_multiple_usage_with_different_distributions",
        name = """
            Test multiple usage with different distributions
            ("${expr("matrix.distributions.distribution1.user-id")}"
            / "${expr("matrix.distributions.distribution2.user-id")}"
            / "${expr("matrix.distributions.distribution3.user-id")}")
            on ${expr("matrix.environment")}
        """.trimIndent().replace("\n", " "),
        _customArguments = mapOf(
            "strategy" to mapOf(
                "fail-fast" to false,
                "matrix" to mapOf(
                    "environment" to environments,
                    "distributions" to listOf(
                        mapOf(
                            "distribution1" to debian,
                            "distribution2" to ubuntu2004,
                            "distribution3" to ubuntu1804
                        ),
                        mapOf(
                            "distribution1" to debian,
                            "distribution2" to ubuntu1804,
                            "distribution3" to ubuntu2004
                        ),
                        mapOf(
                            "distribution1" to ubuntu2004,
                            "distribution2" to debian,
                            "distribution3" to ubuntu1804
                        ),
                        mapOf(
                            "distribution1" to ubuntu2004,
                            "distribution2" to ubuntu1804,
                            "distribution3" to debian
                        ),
                        mapOf(
                            "distribution1" to ubuntu1804,
                            "distribution2" to debian,
                            "distribution3" to ubuntu2004
                        ),
                        mapOf(
                            "distribution1" to ubuntu1804,
                            "distribution2" to ubuntu2004,
                            "distribution3" to debian
                        )
                    )
                )
            )
        )
    ) {
        usesSelf(
            name = "Execute action for ${expr("matrix.distributions.distribution1.user-id")}",
            action = SetupWslV3(
                distribution = Distribution.Custom(expr("matrix.distributions.distribution1.user-id"))
            )
        )
        usesSelf(
            name = "Execute action for ${expr("matrix.distributions.distribution2.user-id")}",
            action = SetupWslV3(
                distribution = Distribution.Custom(expr("matrix.distributions.distribution2.user-id"))
            )
        )
        usesSelf(
            name = "Execute action for ${expr("matrix.distributions.distribution3.user-id")}",
            action = SetupWslV3(
                distribution = Distribution.Custom(expr("matrix.distributions.distribution3.user-id")),
                setAsDefault = false
            )
        )
        executeActionStep = usesSelf(
            name = "Execute action for ${expr("matrix.distributions.distribution1.user-id")} again",
            action = SetupWslV3(
                distribution = Distribution.Custom(expr("matrix.distributions.distribution1.user-id"))
            )
        )
        verifyCommandResult(
            name = "Test - the default distribution should be the last installed distribution with set-as-default true",
            actualCommand = """
                cat
                <(wsl.exe --list || true)
                <(wsl.exe --list || true | iconv -f UTF-16LE -t UTF-8)
                <(wslconfig.exe /list || true)
                <(wslconfig.exe /list || true | iconv -f UTF-16LE -t UTF-8)
            """.trimIndent().replace("\n", " "),
            expectedPattern = """*${expr("matrix.distributions.distribution2.wsl-id")}\ \(Default\)*"""
        )
        verifyInstalledDistribution(
            name = "Test - wsl-bash should use the last installed distribution with set-as-default true",
            expectedPatternExpression = "matrix.distributions.distribution2.match-pattern"
        )
    }

    testJob(
        id = "test_multiple_usage_with_same_distribution",
        name = """Test multiple usage with "${expr("matrix.distribution.user-id")}" distribution on ${expr("matrix.environment")}""",
        _customArguments = mapOf(
            "strategy" to mapOf(
                "fail-fast" to false,
                "matrix" to mapOf(
                    "environment" to environments,
                    "distribution" to distributions,
                    "distribution2" to listOf(debian),
                    "exclude" to environments.map {
                        mapOf(
                            "environment" to it,
                            "distribution" to debian,
                            "distribution2" to debian
                        )
                    },
                    "include" to environments.map {
                        mapOf(
                            "environment" to it,
                            "distribution" to debian,
                            "distribution2" to ubuntu2004
                        )
                    }
                )
            )
        )
    ) {
        usesSelf(
            action = executeAction.copy(
                additionalPackages = listOf("bash")
            )
        )
        usesSelf(
            name = "Update distribution",
            action = executeAction.copy(
                update = true
            ),
            // work-around for https://bugs.kali.org/view.php?id=6672
            condition = "matrix.distribution.user-id != 'kali-linux'"
        )
        executeActionStep = usesSelf(
            name = "Install default absent tool",
            action = executeAction.copy(
                additionalPackages = listOf(expr("matrix.distribution.default-absent-tool"))
            )
        )
        runAfterSuccess(
            name = "Test - ${expr("matrix.distribution.default-absent-tool")} should be installed",
            command = "${expr("matrix.distribution.default-absent-tool")} --version"
        )
        executeActionStep = usesSelfAfterSuccess(
            name = "Execute action for ${expr("matrix.distribution2.user-id")}",
            action = SetupWslV3(
                distribution = Distribution.Custom(expr("matrix.distribution2.user-id"))
            )
        )
        verifyInstalledDistribution(
            name = """Test - "${expr("matrix.distribution2.user-id")}" should be the default distribution after installation""",
            expectedPatternExpression = "matrix.distribution2.match-pattern"
        )
        executeActionStep = usesSelfAfterSuccess(
            name = "Re-execute action",
            action = executeAction
        )
        verifyInstalledDistribution(
            name = """Test - "${expr("matrix.distribution2.user-id")}" should still be the default distribution after re-running for "${expr("matrix.distribution.user-id")}"""",
            expectedPatternExpression = "matrix.distribution2.match-pattern"
        )
        executeActionStep = usesSelfAfterSuccess(
            name = "Set as default",
            action = executeAction.copy(
                setAsDefault = true
            )
        )
        verifyInstalledDistribution(
            name = """Test - "${expr("matrix.distribution.user-id")}" should be the default distribution after re-running with set-as-default true"""
        )
    }

    testJob(
        id = "test_distribution_specific_wsl_bash_scripts",
        name = "Test distribution specific wsl-bash scripts on ${expr("matrix.environment")} (without ${expr("matrix.distributions.incompatibleUbuntu")})",
        _customArguments = mapOf(
            "strategy" to mapOf(
                "fail-fast" to false,
                "matrix" to mapOf(
                    "environment" to environments,
                    // ubuntu2004 and ubuntu2204 currently have the same wsl-id
                    // so their distribution specific wsl_bash scripts will clash
                    // and thus cannot be tested together
                    "distributions" to listOf(ubuntu2204, ubuntu2004)
                        .map { incompatibleUbuntu ->
                            distributions
                                .filter { it != incompatibleUbuntu }
                                .mapIndexed<Map<String, String>, Pair<String, Any>> { i, distribution ->
                                    "distribution${i + 1}" to distribution
                                }
                                .toMutableList()
                                .apply {
                                    add(0, "incompatibleUbuntu" to incompatibleUbuntu["user-id"]!!)
                                }
                                .toMap()
                        }
                )
            )
        )
    ) {
        (1 until distributions.size)
            .associateWith {
                usesSelf(
                    name = "Execute action for ${expr("matrix.distributions.distribution$it.user-id")}",
                    action = SetupWslV3(
                        distribution = Distribution.Custom(expr("matrix.distributions.distribution$it.user-id")),
                        additionalPackages = if (it == 2) listOf("bash") else null,
                        setAsDefault = if (it >= 3) false else null
                    )
                )
            }
            .forEach { (i, localExecuteActionStep) ->
                executeActionStep = localExecuteActionStep
                verifyInstalledDistribution(
                    name = "Test - wsl-bash_${expr("matrix.distributions.distribution$i.user-id")} should use the correct distribution",
                    conditionTransformer = if (distributions[i] == ubuntu2004) {
                        { executeActionStep.getSuccessNotOnDistributionCondition(i, "Ubuntu-20.04") }
                    } else {
                        { it }
                    },
                    // the formula adds 1 to the indices from ubuntu2004 on
                    // to mitigate the double entry for the previous index
                    shell = Shell.Custom("wsl-bash_${distributions[min(1, i / (distributions.indexOf(ubuntu2004) + 1)) + i - 1]["user-id"]} {0}"),
                    expectedPatternExpression = "matrix.distributions.distribution$i.match-pattern"
                )
                if (distributions[i] == ubuntu2004) {
                    verifyInstalledDistribution(
                        name = "Test - wsl-bash_${expr("matrix.distributions.distribution$i.user-id")} should use the correct distribution",
                        conditionTransformer = { executeActionStep.getSuccessNotOnDistributionCondition(i, "Ubuntu-22.04") },
                        shell = Shell.Custom("wsl-bash_${distributions[i]["user-id"]} {0}"),
                        expectedPatternExpression = "matrix.distributions.distribution$i.match-pattern"
                    )
                }
            }

    }
}

fun JobBuilder<*>.commonTests() {
    runAfterSuccess(
        name = "Test - wsl-bash should be available as custom shell"
    )
    verifyFailure(
        name = "Test - wsl-bash should fail if the script fails",
        provocationCommand = "false",
        verificationShell = Cmd,
        verificationTransformer = { provocationStep, _ ->
            // do not just rely on false here, but explicitly use exit
            // in case failing commands do not make the script fail
            // and use "shell = Cmd" to capture that the wrapper script is hiding errors
            "IF '${expr("${provocationStep.outcome}") }' NEQ 'failure' EXIT /B 1"
        }
    )
    verifyFailure(
        name = "Test - wsl-bash should fail if one of the commands fails",
        provocationCommand = """
            false
            :
        """,
        // do not just rely on false here, but explicitly use exit
        // in case failing commands do not make the script fail
        verificationTransformer = { _, command -> "$command || exit 1" }
    )
    verifyFailure(
        name = "Test - wsl-bash should fail if an undefined variable is used",
        provocationCommand = "\$foo"
    )
    verifyFailure(
        name = "Test - wsl-bash should fail if any command in a pipe fails",
        provocationCommand = "false | true"
    )
    verifyCommandResult(
        name = "Test - the default distribution should be correct",
        actualCommand = """
            cat
            <(wsl.exe --list || true)
            <(wsl.exe --list || true | iconv -f UTF-16LE -t UTF-8)
            <(wslconfig.exe /list || true)
            <(wslconfig.exe /list || true | iconv -f UTF-16LE -t UTF-8)
        """.trimIndent().replace("\n", " "),
        expectedPattern = """*${expr("matrix.distribution.wsl-id")}\ \(Default\)*"""
    )
    verifyInstalledDistribution(
        name = "Test - wsl-bash should use the correct distribution"
    )
    runAfterSuccess(
        name = "Test - multi-line commands should not be disturbed by CRLF line endings",
        command = """
            : # this comment catches the CR if present
            ! grep -q $'\r' "$0" # this comment catches the CR if present
        """
    )
}

fun JobBuilder<*>.usesSelfAfterSuccess(
    name: String = "Execute action",
    action: SetupWslV3
) = usesSelf(
    name = name,
    action = action,
    condition = executeActionStep.successCondition
)

fun JobBuilder<*>.usesSelf(
    name: String = "Execute action",
    action: SetupWslV3,
    condition: String? = null,
    continueOnError: Boolean? = null
) = uses(
    name = name,
    action = action,
    condition = condition,
    continueOnError = continueOnError,
    _customArguments = mapOf(
        "uses" to "./"
    )
)

fun JobBuilder<*>.deleteWslBashOnAlpine() = deleteWslBash(
    conditionTransformer = { executeActionStep.successOnAlpineCondition }
)

fun JobBuilder<*>.deleteWslBash(
    wslBashPathExpression: String = executeActionStep.outputs.wslShellWrapperPath,
    conditionTransformer: (String) -> String = { it }
) = run(
    name = "Delete wsl-bash",
    condition = conditionTransformer(executeActionStep.successCondition).trimIndent(),
    shell = Cmd,
    command = """DEL /F "${expr(wslBashPathExpression)}""""
)

fun JobBuilder<*>.runAfterSuccess(
    name: String,
    conditionTransformer: (String) -> String = { it },
    shell: Shell? = wslBash,
    command: String = ":",
    continueOnError: Boolean? = null
) = run(
    name = name,
    condition = conditionTransformer(executeActionStep.successCondition).trimIndent(),
    shell = shell,
    command = command.trimIndent(),
    continueOnError = continueOnError
)

fun JobBuilder<*>.verifyFailure(
    name: String,
    conditionTransformer: (String) -> String = { it },
    provocationShell: Shell = wslBash,
    provocationCommand: String = ":",
    verificationShell: Shell? = wslBash,
    verificationTransformer: (CommandStep, String) -> String = { _, command -> command }
) {
    val provocationStep = runAfterSuccess(
        name = "$name (provocation)",
        conditionTransformer = conditionTransformer,
        shell = provocationShell,
        command = provocationCommand,
        continueOnError = true
    )
    runAfterSuccess(
        name = "$name (verification)",
        conditionTransformer = conditionTransformer,
        shell = verificationShell,
        command = verificationTransformer(
            provocationStep,
            "[ '${expr("${provocationStep.outcome}")}' == 'failure' ]"
        )
    )
}

fun JobBuilder<*>.verifyInstalledDistribution(
    name: String,
    conditionTransformer: (String) -> String = { it },
    shell: Shell = wslBash,
    expectedPatternExpression: String = "matrix.distribution.match-pattern"
) = verifyCommandResult(
    name = name,
    conditionTransformer = conditionTransformer,
    actualCommand = """
        cat
        <(lsb_release -a || true)
        <(uname -a || true)
        <([ -d /etc ] && find /etc -maxdepth 1 -type f \( -name '*release' -or -name 'issue*' \) -exec cat {} + || true)
        <([ -d /etc/products.d ] && find /etc/products.d -maxdepth 1 -type f -name '*.prod' -exec cat {} + || true)
        <([ -f /proc/version ] && cat /proc/version || true)
    """.trimIndent().replace("\n", " "),
    shell = shell,
    expectedPattern = expr(expectedPatternExpression)
)

fun JobBuilder<*>.verifyCommandResult(
    name: String,
    conditionTransformer: (String) -> String = { it },
    actualCommand: String,
    shell: Shell = wslBash,
    expected: String? = null,
    expectedPattern: String? = null
) {
    require((expected != null) || (expectedPattern != null)) {
        "Either expected or expectedPattern must be non-null"
    }
    require(
        ((expected != null) && (expectedPattern == null))
                || ((expected == null) && (expectedPattern != null))
    ) {
        "Either expected or expectedPattern must be non-null, but not both"
    }
    runAfterSuccess(
        name = name,
        conditionTransformer = conditionTransformer,
        shell = shell,
        command = if (expected != null) """
            ${actualCommand.trimIndent()}
            [ "$(${actualCommand.trimIndent()})" == '${expected.trimIndent()}' ]
        """ else if (expectedPattern != null) """
            ${actualCommand.trimIndent()}
            [[ "$(${actualCommand.trimIndent()})" == ${expectedPattern.trimIndent()} ]]
        """ else error("Erroneous method input validation")
    )
}

val Step.successCondition
    get() = """
        always()
        && (${outcome.eq(Success)})
    """.trimIndent()

val Step.successOnAlpineCondition
    get() = """
        always()
        && (${outcome.eq(Success)})
        && (matrix.distribution.user-id == 'Alpine')
    """.trimIndent()

val Step.successNotOnUbuntu2404Condition
    get() = """
        always()
        && (${outcome.eq(Success)})
        && (matrix.distribution.user-id != 'Ubuntu-24.04')
    """.trimIndent()

fun Step.getSuccessNotOnDistributionCondition(i: Int, distribution: String) = """
    always()
    && (${outcome.eq(Success)})
    && (matrix.distributions.distribution$i.user-id != '$distribution')
""".trimIndent()
