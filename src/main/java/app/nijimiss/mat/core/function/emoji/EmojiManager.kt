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

package app.nijimiss.mat.core.function.emoji

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.core.database.EmojiStore
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import com.fasterxml.jackson.databind.ObjectMapper
import page.nafuchoco.neobot.api.module.NeoModuleLogger

class EmojiManager(
    private val emojiManagerConfig: EmojiManagerConfig,
    private val emojiStore: EmojiStore,
    private val requestManager: ApiRequestManager,
) {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger

    fun getEmoji(name: String): Emoji? {
        var emoji: Emoji? = null

        // 登録済みの絵文字を取得
        emojiStore.getEmoji(name)?.let {
            emoji = it
        }

        // リクエスト中の絵文字を取得
        if (emoji == null) {
            emojiStore.getEmojiRequest(name)?.let {
                emoji = it
            }
        }

        // Misskey APIから絵文字情報を取得
        if (emoji == null) {
            val searchEmoji = app.nijimiss.mat.core.requests.misskey.endpoints.Emoji(name)
            requestManager.addRequest(searchEmoji, object : ApiResponseHandler {
                override fun onSuccess(response: ApiResponse?) {
                    emoji = MAPPER.readValue(response?.body, RegisteredEmoji::class.java)
                }

                override fun onFailure(response: ApiResponse?) {
                    // do nothing
                }
            }).join()
        }

        return emoji
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
