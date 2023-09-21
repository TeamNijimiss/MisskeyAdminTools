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

package app.nijimiss.mat.core.function.emoji

import com.fasterxml.jackson.annotation.JsonProperty

data class RegisteredEmoji(
    val id: String? = null,
    val aliases: List<String>? = null,
    @get:JsonProperty("name") @field:JsonProperty("name")
    override val emojiName: String,
    /** this field is always return empty string */
    override val imageFileId: String = "",
    val category: String? = null,
    val host: String? = null,
    @get:JsonProperty("url") @field:JsonProperty("url")
    override val imageUrl: String,
    override val license: String? = null,
    @get:JsonProperty("isSensitive") @field:JsonProperty("isSensitive")
    override val sensitive: Boolean,
    override val localOnly: Boolean,
    @get:JsonProperty("roleIdsThatCanBeUsedThisEmojiAsReaction") @field:JsonProperty("roleIdsThatCanBeUsedThisEmojiAsReaction")
    val roleIDSThatCanBeUsedThisEmojiAsReaction: List<String>? = null,
    override val comment: String? = null
) : Emoji
