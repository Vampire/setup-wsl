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

package net.kautler.util

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.Severity
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.properties.PropertyDelegateProvider as KotlinPropertyDelegateProvider

sealed class Property<out T>(
    private val default: () -> T,
    private var propertyName: String,
    private var project: Project
) : ReadOnlyProperty<Any?, T> {
    protected abstract fun doGetValue(project: Project, propertyName: String): T?

    fun getValue() = doGetValue(project, propertyName) ?: default.invoke()

    override fun getValue(thisRef: Any?, property: KProperty<*>) = getValue()

    companion object {
        fun boolean(
            default: () -> Boolean = { false },
            propertyName: String? = null,
            project: Project? = null
        ) = PropertyDelegateProvider(
            default,
            propertyName,
            project,
            ::BooleanProperty
        )

        fun Project.boolean(
            default: () -> Boolean = { false },
            propertyName: String? = null
        ) = boolean(default, propertyName, this)

        fun Task.boolean(
            default: () -> Boolean = { false },
            propertyName: String? = null
        ) = boolean(default, propertyName, project)

        fun boolean(
            default: Boolean,
            propertyName: String? = null,
            project: Project? = null
        ) = boolean({ default }, propertyName, project)

        fun Project.boolean(
            default: Boolean,
            propertyName: String? = null
        ) = boolean(default, propertyName, this)

        fun Task.boolean(
            default: Boolean,
            propertyName: String? = null
        ) = boolean(default, propertyName, project)

        fun boolean(
            project: Project,
            propertyName: String,
            default: () -> Boolean = { false }
        ): Property<Boolean> = BooleanProperty(default, propertyName, project)

        @JvmName("booleanProperty")
        fun Project.boolean(
            propertyName: String,
            default: () -> Boolean = { false }
        ) = boolean(this, propertyName, default)

        fun Task.boolean(
            propertyName: String,
            default: () -> Boolean = { false }
        ) = boolean(project, propertyName, default)

        fun boolean(
            project: Project,
            propertyName: String,
            default: Boolean
        ) = boolean(project, propertyName) { default }

        @JvmName("booleanProperty")
        fun Project.boolean(
            propertyName: String,
            default: Boolean
        ) = boolean(this, propertyName, default)

        fun Task.boolean(
            propertyName: String,
            default: Boolean
        ) = boolean(project, propertyName, default)

        fun string(
            default: () -> String,
            propertyName: String? = null,
            project: Project? = null
        ) = PropertyDelegateProvider(
            default,
            propertyName,
            project,
            ::StringProperty
        )

        fun Project.string(
            default: () -> String,
            propertyName: String? = null
        ) = string(default, propertyName, this)

        fun Task.string(
            default: () -> String,
            propertyName: String? = null
        ) = string(default, propertyName, project)

        fun string(
            default: String,
            propertyName: String? = null,
            project: Project? = null
        ) = string({ default }, propertyName, project)

        fun Project.string(
            default: String,
            propertyName: String? = null
        ) = string(default, propertyName, this)

        fun Task.string(
            default: String,
            propertyName: String? = null
        ) = string(default, propertyName, project)

        fun string(
            project: Project,
            propertyName: String,
            default: () -> String
        ): Property<String> = StringProperty(default, propertyName, project)

        @JvmName("stringProperty")
        fun Project.string(
            propertyName: String,
            default: () -> String
        ) = string(this, propertyName, default)

        fun Task.string(
            propertyName: String,
            default: () -> String
        ) = string(project, propertyName, default)

        fun string(
            project: Project,
            propertyName: String,
            default: String
        ) = string(project, propertyName) { default }

        @JvmName("stringProperty")
        fun Project.string(
            propertyName: String,
            default: String
        ) = string(this, propertyName, default)

        fun Task.string(
            propertyName: String,
            default: String
        ) = string(project, propertyName, default)

        fun optionalString(
            propertyName: String? = null,
            project: Project? = null
        ) = PropertyDelegateProvider(
            { null },
            propertyName,
            project,
            ::OptionalStringProperty
        )

        fun Project.optionalString(
            propertyName: String? = null
        ) = optionalString(propertyName, this)

        fun Task.optionalString(
            propertyName: String? = null
        ) = optionalString(propertyName, project)

        fun optionalString(
            project: Project,
            propertyName: String
        ): Property<String?> = OptionalStringProperty(propertyName, project)

        @JvmName("optionalStringProperty")
        fun Project.optionalString(
            propertyName: String
        ) = optionalString(this, propertyName)

        fun Task.optionalString(
            propertyName: String
        ) = optionalString(project, propertyName)

        fun double(
            default: () -> Double = { 0.0 },
            propertyName: String? = null,
            project: Project? = null
        ) = PropertyDelegateProvider(
            default,
            propertyName,
            project,
            ::DoubleProperty
        )

        fun Project.double(
            default: () -> Double = { 0.0 },
            propertyName: String? = null
        ) = double(default, propertyName, this)

        fun Task.double(
            default: () -> Double = { 0.0 },
            propertyName: String? = null
        ) = double(default, propertyName, project)

        fun double(
            default: Double,
            propertyName: String? = null,
            project: Project? = null
        ) = double({ default }, propertyName, project)

        fun Project.double(
            default: Double,
            propertyName: String? = null
        ) = double(default, propertyName, this)

        fun Task.double(
            default: Double,
            propertyName: String? = null
        ) = double(default, propertyName, project)

        fun double(
            project: Project,
            propertyName: String,
            default: () -> Double = { 0.0 }
        ): Property<Double> = DoubleProperty(default, propertyName, project)

        @JvmName("doubleProperty")
        fun Project.double(
            propertyName: String,
            default: () -> Double = { 0.0 }
        ) = double(this, propertyName, default)

        fun Task.double(
            propertyName: String,
            default: () -> Double = { 0.0 }
        ) = double(project, propertyName, default)

        fun double(
            project: Project,
            propertyName: String,
            default: Double
        ) = double(project, propertyName) { default }

        @JvmName("doubleProperty")
        fun Project.double(
            propertyName: String,
            default: Double
        ) = double(this, propertyName, default)

        fun Task.double(
            propertyName: String,
            default: Double
        ) = double(project, propertyName, default)
    }
}

class PropertyDelegateProvider<out T>(
    private val default: () -> T,
    private val propertyName: String? = null,
    private val project: Project? = null,
    private val delegateFactory: (() -> T, String, Project) -> Property<T>
) : KotlinPropertyDelegateProvider<Any?, Property<T>> {
    operator fun provideDelegate(thisRef: Project, property: KProperty<*>) =
        delegateFactory(default, propertyName ?: property.name, project ?: thisRef)

    operator fun provideDelegate(thisRef: Task, property: KProperty<*>) =
        provideDelegate(thisRef.project, property)

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        project?.let {
            provideDelegate(it, property)
        } ?: error(
            "Property '${property.name}' must be declared on 'Project' or 'Task', " +
                    "or 'Project' must be given explicitly"
        )
}

private class OptionalStringProperty(
    default: () -> String?,
    propertyName: String,
    project: Project
) : Property<String?>(default, propertyName, project) {
    constructor(propertyName: String, project: Project) : this({ null }, propertyName, project)

    override fun doGetValue(project: Project, propertyName: String) =
        findProperty(project, propertyName)
}

private class StringProperty(
    default: () -> String,
    propertyName: String,
    project: Project
) : Property<String>(default, propertyName, project) {
    override fun doGetValue(project: Project, propertyName: String) =
        findProperty(project, propertyName)
}

private class BooleanProperty(
    default: () -> Boolean = { false },
    propertyName: String,
    project: Project
) : Property<Boolean>(default, propertyName, project) {
    override fun doGetValue(project: Project, propertyName: String) =
        findProperty(project, propertyName)?.toBoolean()
}

private class DoubleProperty(
    default: () -> Double = { 0.0 },
    propertyName: String,
    project: Project
) : Property<Double>(default, propertyName, project) {
    override fun doGetValue(project: Project, propertyName: String) =
        findProperty(project, propertyName)?.let {
            it.toDoubleOrNull() ?: error("'$it' is not a valid value for double property '$propertyName'")
        }
}

private fun findProperty(project: Project, propertyName: String): String? {
    var result = project.findProperty("${project.rootProject.name}.$propertyName") as String?
    if (result.isNullOrBlank()) {
        result = project.findProperty(propertyName) as String?
    }
    return if (result.isNullOrBlank()) null else result
}

fun String?.verifyPropertyIsSet(problemReporter: ProblemReporter, propertyName: String, rootProjectName: String) {
    if (isNullOrBlank()) {
        throw problemReporter.throwing(
            IllegalStateException(),
            ProblemId.create(
                "properties-'$propertyName'-and-'$rootProjectName.$propertyName'-missing",
                "The properties '$propertyName' and '$rootProjectName.$propertyName' are missing",
                ProblemGroup.create("required-properties", "Required Properties")
            )
        ) {
            solution("Set the project property '$propertyName'")
            solution("Set the project property '$rootProjectName.$propertyName'")
            solution("If both are set, the latter will be effective")
            severity(Severity.ERROR)
        }
    }
}
