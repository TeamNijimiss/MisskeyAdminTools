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

package app.nijimiss.mat

import com.fasterxml.jackson.annotation.JsonProperty

data class Authentication(
    @get:JsonProperty("instanceHostname") @field:JsonProperty("instanceHostname")
    val instanceHostname: String? = null,

    @get:JsonProperty("instanceToken") @field:JsonProperty("instanceToken")
    val instanceToken: String? = null,
)

data class MATConfig(
    val authentication: Authentication? = null,
    val discord: DiscordConfig? = null,
    val options: Options? = null,
    val debug: Boolean = false,
)

data class DiscordConfig(
    val targetGuild: Long? = null,
)

data class Options(
    val reportWatcher: ReportWatcherOptions? = null
)

data class ReportWatcherOptions(
    @get:JsonProperty("reportWatcherEnabled") @field:JsonProperty("reportWatcherEnabled")
    val enabled: Boolean = true,
    val targetReportChannel: Long? = null,
    val silenceRoleId: String? = null,
    val excludeDiscordRoles: List<Long>? = null,
)
