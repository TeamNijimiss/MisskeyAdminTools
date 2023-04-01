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

package app.nijimiss.mat.api.misskey

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class User(
    val id: String? = null,
    val name: String? = null,
    val username: String? = null,
    val host: String? = null,

    @get:JsonProperty("avatarUrl") @field:JsonProperty("avatarUrl")
    val avatarURL: String? = null,

    val avatarBlurhash: String? = null,

    @get:JsonProperty("isBot") @field:JsonProperty("isBot")
    val isBot: Boolean? = null,

    @get:JsonProperty("isCat") @field:JsonProperty("isCat")
    val isCat: Boolean? = null,

    val emojis: Emojis? = null,
    val onlineStatus: String? = null,
    val badgeRoles: List<BadgeRole>? = null
)
