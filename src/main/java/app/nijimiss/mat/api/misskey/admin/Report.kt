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

package app.nijimiss.mat.api.misskey.admin

import app.nijimiss.mat.api.misskey.FullUser
import com.fasterxml.jackson.annotation.JsonProperty

data class Report(
    val id: String? = null,
    val createdAt: String? = null,
    val comment: String? = null,
    val resolved: Boolean? = null,

    @get:JsonProperty("reporterId") @field:JsonProperty("reporterId")
    val reporterID: String? = null,

    @get:JsonProperty("targetUserId") @field:JsonProperty("targetUserId")
    val targetUserID: String? = null,

    @get:JsonProperty("assigneeId") @field:JsonProperty("assigneeId")
    val assigneeID: Any? = null,

    val reporter: FullUser? = null,
    val targetUser: FullUser? = null,
    val assignee: Any? = null,
    val forwarded: Boolean? = null,
    val category: String? = null,
)
