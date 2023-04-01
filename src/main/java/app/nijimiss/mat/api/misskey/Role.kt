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

import com.fasterxml.jackson.annotation.JsonProperty

data class Role(
    val id: String? = null,
    val name: String? = null,
    val color: String? = null,

    @get:JsonProperty("displayOrder") @field:JsonProperty("displayOrder")
    val displayOrder: Long? = null,

    @get:JsonProperty("iconUrl") @field:JsonProperty("iconUrl")
    val iconURL: String? = null,

    val description: String? = null,

    @get:JsonProperty("isModerator") @field:JsonProperty("isModerator")
    val isModerator: Boolean? = null,

    @get:JsonProperty("isAdministrator") @field:JsonProperty("isAdministrator")
    val isAdministrator: Boolean? = null
)
