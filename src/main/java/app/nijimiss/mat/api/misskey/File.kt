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

import com.fasterxml.jackson.annotation.JsonProperty

data class File(
    val id: String? = null,
    val createdAt: String? = null,
    val name: String? = null,
    val type: String? = null,
    val md5: String? = null,
    val size: Long? = null,

    @get:JsonProperty("isSensitive") @field:JsonProperty("isSensitive")
    val isSensitive: Boolean? = null,

    val blurhash: String? = null,
    val properties: Properties? = null,
    val url: String? = null,

    @get:JsonProperty("thumbnailUrl") @field:JsonProperty("thumbnailUrl")
    val thumbnailURL: String? = null,

    val comment: String? = null,

    @get:JsonProperty("folderId") @field:JsonProperty("folderId")
    val folderID: String? = null,

    val folder: Folder? = null,

    @get:JsonProperty("userId") @field:JsonProperty("userId")
    val userID: String? = null,

    val user: User? = null
) {
    data class Properties(
        val width: Long? = null,
        val height: Long? = null,
        val avgColor: String? = null
    )
}
