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

data class Emoji(
    val id: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val aliases: List<String>? = null,
    val name: String? = null,
    val category: String? = null,
    val host: String? = null,
    val url: String? = null,
    val license: String? = null,

    @get:JsonProperty("isSensitive") @field:JsonProperty("isSensitive")
    val isSensitive: Boolean? = null,

    val localOnly: Boolean? = null,
    val requestedBy: String? = null,
    val memo: String? = null,

    @get:JsonProperty("roleIdsThatCanBeUsedThisEmojiAsReaction") @field:JsonProperty("roleIdsThatCanBeUsedThisEmojiAsReaction")
    val roleIDSThatCanBeUsedThisEmojiAsReaction: List<String>? = null
)
