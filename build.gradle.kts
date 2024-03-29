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

plugins {
    id(libs.plugins.convention.dependencies.get().pluginId)
    id(libs.plugins.convention.node.get().pluginId)
    id(libs.plugins.convention.github.actions.get().pluginId)
    id(libs.plugins.convention.readme.get().pluginId)
    id(libs.plugins.convention.publishing.get().pluginId)
}
