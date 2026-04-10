/*
 * Copyright 2026 Björn Kautler
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

package net.kautler.util

import net.kautler.util.ReleaseBody.Parameters
import org.ajoberstar.grgit.Grgit
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.kohsuke.github.GitHubBuilder
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

abstract class ReleaseBody : ValueSource<String, Parameters> {
    override fun obtain(): String {
        val releaseBody = runCatching {
            Grgit.open {
                currentDir = parameters.projectDirectory.get().asFile
            }
        }
            .getOrNull()
            ?.use {
                it
                    .log {
                        includes.add(parameters.branchName.get())
                        GitHubBuilder()
                            .withOAuthToken(parameters.githubToken.get())
                            .build()
                            .getRepository(parameters.repositoryName.get())
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
            }
            ?: ""

        if (isHeadless()) {
            return releaseBody
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
                    when (
                        showOptionDialog(
                            parentFrame,
                            JScrollPane(textArea),
                            "Release Body",
                            DEFAULT_OPTION,
                            QUESTION_MESSAGE,
                            null,
                            arrayOf("OK", resetButton),
                            null
                        )
                    ) {
                        OK_OPTION -> textArea.text!!
                        else -> releaseBody
                    }
                } finally {
                    parentFrame.dispose()
                }
            )
        }

        return result.join()
    }

    interface Parameters : ValueSourceParameters {
        val projectDirectory: DirectoryProperty
        val githubToken: Property<String>
        val repositoryName: Property<String>
        val branchName: Property<String>
    }
}
