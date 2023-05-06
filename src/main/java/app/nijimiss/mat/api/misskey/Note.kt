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

data class Note(
    val id: String? = null,
    val createdAt: String? = null,
    val uri: String? = null,
    val url: String? = null,

    @get:JsonProperty("userId") @field:JsonProperty("userId")
    val userID: String? = null,

    val user: User? = null,
    val text: String? = null,
    val cw: String? = null,
    val visibility: String? = null,
    val localOnly: Boolean? = null,
    val reactionAcceptance: String? = null,
    val renoteCount: Long? = null,
    val repliesCount: Long? = null,
    val reactions: Map<String, Int>? = null,
    val reactionEmojis: Emojis? = null,
    val emojis: Emojis? = null,

    @get:JsonProperty("fileIds") @field:JsonProperty("fileIds")
    val fileIDS: List<String>? = null,

    val files: List<File>? = null,

    @get:JsonProperty("replyId") @field:JsonProperty("replyId")
    val replyID: String? = null,
    val reply: Note? = null,

    @get:JsonProperty("renoteId") @field:JsonProperty("renoteId")
    val renoteID: String? = null,

    val renote: Note? = null,
    val poll: Poll? = null,
    val mentions: List<String>? = null,

    @get:JsonProperty("channelId") @field:JsonProperty("channelId")
    val channelID: String? = null,

    val channel: Channel? = null,
    val tags: List<String>? = null,

    @get:JsonProperty("isHidden") @field:JsonProperty("isHidden")
    val isHidden: Boolean? = null,
)
