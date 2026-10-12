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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id(libs.plugins.dependency.analysis.get().pluginId)
}

dependencies {
    compileOnly(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    compileOnly(files(kotlinWrappers.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(plugin(libs.plugins.versions))
    // part of work-around for https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1672
    //implementation(plugin(libs.plugins.dependency.analysis))
    implementation(plugin(libs.plugins.release))
    implementation(plugin(libs.plugins.grgit))
    implementation(plugin(libs.plugins.kotlin.multiplatform))
    implementation(platform(libs.build.kotlinx.serialization.bom))
    implementation(libs.build.kotlinx.serialization.json)
    implementation(libs.build.github.api)
    implementation(libs.build.snakeyaml)
    compileOnly(embeddedKotlin("compiler-embeddable"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencyAnalysis {
    structure {
        bundle("org.ajoberstar.grgit.service.gradle.plugin") {
            includeDependency("org.ajoberstar.grgit.service:org.ajoberstar.grgit.service.gradle.plugin")
            includeDependency("org.ajoberstar.grgit:grgit-core")
        }
        bundle("org.jetbrains.kotlin.multiplatform.gradle.plugin") {
            includeDependency("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin")
            includeDependency("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
        }
    }
    issues {
        all {
            onAny {
                severity("fail")
            }
            onDuplicateClassWarnings {
                // work-around for https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1629
                severity("fail")
                // present in kotlin-compiler-embeddable and kotlin-gradle-plugin
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/AllJavaSourcesInProjectScope")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/ClasspathRootsResolver")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/ClasspathRootsResolver$Companion")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/ClasspathRootsResolver$RootWithPrefix")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/ClasspathRootsResolver$RootsAndModules")
                exclude($$$"org/jetbrains/kotlin/cli/jvm/compiler/ClasspathRootsResolver$tryLoadVersionSpecificModuleInfo$$inlined$sortedBy$1")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CliBindingTrace")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CliJavaModuleUtilsKt")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CliMetadataFinderFactory")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CliTraceHolder")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CliVirtualFileFinder")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CliVirtualFileFinderFactory")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CompatKt")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CompileEnvironmentException")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CompileEnvironmentUtil")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/CoreEnvironmentUtilsKt")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/CoreEnvironmentUtilsKt$allSourceFilesSequence$1")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/DumbJavaModuleGraphHelper")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/DummyJavaFileCodeStyleFacadeFactory")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/DummyJavaFileCodeStyleFacadeFactory$DummyJavaFileCodeStyleFacade")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/EnvironmentConfigFiles")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/IdeaExtensionPoints")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/IdeaStandaloneExecutionSetup")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/IncrementalCompilationContextUtilsKt")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/JavaLanguageLevelKt")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/JavaLanguageLevelKt$EntriesMappings")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/JvmPackagePartProvider")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/JvmPackagePartProviderKt")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/KotlinCliJavaFileManagerImpl")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCliJavaFileManagerImpl$Companion")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCliJavaFileManagerImpl$findPackage$3")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/KotlinCliJavaFileManagerImplKt")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironment")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironment$Companion")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironmentMode")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironmentMode$Companion")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironmentMode$Production")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreApplicationEnvironmentMode$UnitTest")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$2")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$Companion")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$Companion$getOrCreateApplicationEnvironment$1$2")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$ProjectEnvironment")
                exclude($$$"org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment$special$$inlined$sortBy$1")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreProjectEnvironment")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreUnitTestApplication")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/MockExternalAnnotationsManager")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/MockInferredAnnotationsManager")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/MockInferredAnnotationsManager$Companion")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/NoScopeRecordCliBindingTrace")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/PlatformWriteAccessSupport")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/PsiBasedProjectFileSearchScope")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/SourceFileWithModule")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/ValidSourceFilesFilter")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/VfsBasedProjectEnvironment")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/VfsBasedProjectEnvironment$ClassPathScope")
                exclude($$"org/jetbrains/kotlin/cli/jvm/compiler/VfsBasedProjectEnvironment$DirectoriesScope")
                exclude("org/jetbrains/kotlin/cli/jvm/compiler/VfsBasedProjectEnvironmentKt")
                exclude("org/jetbrains/kotlin/com/intellij/mock/MockProject")
                exclude("org/jetbrains/kotlin/com/intellij/openapi/util/ObjectNode")
                exclude("org/jetbrains/kotlin/com/intellij/openapi/util/ObjectNode$1")
                exclude($$"org/jetbrains/kotlin/com/intellij/openapi/util/ObjectNode$ListNodeChildren")
                exclude($$"org/jetbrains/kotlin/com/intellij/openapi/util/ObjectNode$MapNodeChildren")
                exclude($$"org/jetbrains/kotlin/com/intellij/openapi/util/ObjectNode$NodeChildren")
            }
        }
    }
    reporting {
        printBuildHealth(true)
    }
}

tasks.buildHealth {
    dependsOn(gradle.includedBuilds.map { it.task(":buildHealth") })
}

tasks.check {
    dependsOn(tasks.buildHealth)
}

tasks.dependencyUpdates {
    checkConstraints = true
    checkBuildEnvironmentConstraints = true
    rejectPreReleases = true
}

fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}
