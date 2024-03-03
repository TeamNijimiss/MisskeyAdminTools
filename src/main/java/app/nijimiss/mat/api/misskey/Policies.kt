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

data class Policies(
    val gtlAvailable: Boolean? = null,
    val ltlAvailable: Boolean? = null,
    val canPublicNote: Boolean? = null,
    val mentionLimit: Long? = null,
    val canInvite: Boolean? = null,
    val inviteLimit: Long? = null,
    val inviteLimitCycle: Long? = null,
    val inviteExpirationTime: Long? = null,
    val canManageCustomEmojis: Boolean? = null,
    val canManageAvatarDecorations: Boolean? = null,
    val canSearchNotes: Boolean? = null,
    val canUseAdvancesSearch: Boolean? = null,
    val canUseTranslator: Boolean? = null,
    val canHideAds: Boolean? = null,

    @get:JsonProperty("driveCapacityMb") @field:JsonProperty("driveCapacityMb")
    val driveCapacityMB: Long? = null,

    val alwaysMarkNsfw: Boolean? = null,
    val pinLimit: Long? = null,
    val antennaLimit: Long? = null,
    val wordMuteLimit: Long? = null,
    val webhookLimit: Long? = null,
    val accessTokenLimit: Long? = null,
    val clipLimit: Long? = null,
    val noteEachClipsLimit: Long? = null,
    val userListLimit: Long? = null,
    val userEachUserListsLimit: Long? = null,
    val rateLimitFactor: Double? = null,
    val avatarDecorationLimit: Long? = null
)
