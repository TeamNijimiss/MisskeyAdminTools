/*
 * Copyright 2023 Nafu Satsuki
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

package app.nijimiss.mat.core.function.role

data class RoleSynchronizerConfig(
    val discordTargetChannel: Long = 0,
    val adminRole: Long = 0,
    val roleAssign: List<Roles> = emptyList()
)

data class Roles(
    val discordRole: Long = 0,
    val misskeyRole: String? = null
)
