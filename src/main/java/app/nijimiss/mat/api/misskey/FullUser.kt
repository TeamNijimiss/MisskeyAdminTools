/*
 * Copyright 2024 Nafu Satsuki
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
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class FullUser(
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
    val badgeRoles: List<BadgeRole>? = null,
    val url: String? = null,
    val uri: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastFetchedAt: Any? = null,

    @get:JsonProperty("bannerUrl") @field:JsonProperty("bannerUrl")
    val bannerURL: String? = null,

    val bannerBlurhash: String? = null,

    @get:JsonProperty("isLocked") @field:JsonProperty("isLocked")
    val isLocked: Boolean? = null,

    @get:JsonProperty("isSilenced") @field:JsonProperty("isSilenced")
    val isSilenced: Boolean? = null,

    @get:JsonProperty("isSuspended") @field:JsonProperty("isSuspended")
    val isSuspended: Boolean? = null,

    val description: String? = null,
    val location: String? = null,
    val birthday: String? = null,
    val lang: String? = null,
    val fields: List<Field>? = null,
    val followersCount: Long? = null,
    val followingCount: Long? = null,
    val notesCount: Long? = null,

    @get:JsonProperty("pinnedNoteIds") @field:JsonProperty("pinnedNoteIds")
    val pinnedNoteIDS: List<String>? = null,

    val pinnedNotes: List<Note>? = null,

    @get:JsonProperty("pinnedPageId") @field:JsonProperty("pinnedPageId")
    val pinnedPageID: Any? = null,

    val pinnedPage: Any? = null,
    val publicReactions: Boolean? = null,
    val ffVisibility: String? = null,
    val twoFactorEnabled: Boolean? = null,
    val usePasswordLessLogin: Boolean? = null,
    val securityKeys: Boolean? = null,
    val roles: List<Role>? = null,

    @get:JsonProperty("avatarId") @field:JsonProperty("avatarId")
    val avatarID: String? = null,

    @get:JsonProperty("bannerId") @field:JsonProperty("bannerId")
    val bannerID: String? = null,

    @get:JsonProperty("isModerator") @field:JsonProperty("isModerator")
    val isModerator: Boolean? = null,

    @get:JsonProperty("isAdmin") @field:JsonProperty("isAdmin")
    val isAdmin: Boolean? = null,

    val injectFeaturedNote: Boolean? = null,
    val receiveAnnouncementEmail: Boolean? = null,
    val alwaysMarkNsfw: Boolean? = null,
    val autoSensitive: Boolean? = null,
    val carefulBot: Boolean? = null,
    val autoAcceptFollowed: Boolean? = null,
    val noCrawle: Boolean? = null,

    @get:JsonProperty("isExplorable") @field:JsonProperty("isExplorable")
    val isExplorable: Boolean? = null,

    @get:JsonProperty("isDeleted") @field:JsonProperty("isDeleted")
    val isDeleted: Boolean? = null,

    val hideOnlineStatus: Boolean? = null,
    val hasUnreadSpecifiedNotes: Boolean? = null,
    val hasUnreadMentions: Boolean? = null,
    val hasUnreadAnnouncement: Boolean? = null,
    val hasUnreadAntenna: Boolean? = null,
    val hasUnreadChannel: Boolean? = null,
    val hasUnreadNotification: Boolean? = null,
    val hasPendingReceivedFollowRequest: Boolean? = null,
    val mutedWords: List<Any?>? = null,
    val mutedInstances: List<Any?>? = null,
    val mutingNotificationTypes: List<Any?>? = null,
    val emailNotificationTypes: List<Any?>? = null,
    val showTimelineReplies: Boolean? = null,
    val achievements: List<Achievement>? = null,
    val loggedInDays: Long? = null,
    val policies: Policies? = null
)

typealias Emojis = JsonNode
