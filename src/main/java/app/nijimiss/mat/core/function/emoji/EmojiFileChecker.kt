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

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.api.misskey.Emoji
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.emoji.List
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import devcsrj.okhttp3.logging.HttpLoggingInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import page.nafuchoco.neobot.api.command.CommandContext
import page.nafuchoco.neobot.api.command.SubCommandOption
import java.io.IOException

class EmojiFileChecker(
    private val requestManager: ApiRequestManager,
) : SubCommandOption("check") {
    private val logging: HttpLoggingInterceptor = HttpLoggingInterceptor(LoggerFactory.getLogger(this.javaClass))
    private val httpClient: OkHttpClient = OkHttpClient.Builder().addInterceptor(logging).build()

    override fun onInvoke(context: CommandContext) {
        context.responseSender.sendMessage("絵文字ファイルのチェックを開始します。").queue()
        val failedEmojis = mutableListOf<Emoji>()
        var untilId: String? = null
        do {
            var count = 0
            requestManager.addRequest(List(null, 50, null, untilId), object : ApiResponseHandler {
                override fun onSuccess(response: ApiResponse?) {
                    try {
                        val emojis = MAPPER.readValue(
                            response!!.body, object : TypeReference<kotlin.collections.List<Emoji>>() {})

                        count = emojis.size

                        emojis.forEach { emoji ->
                            val httpRequest: Request = Request.Builder()
                                .url(emoji.url!!)
                                .get()
                                .build()

                            httpClient.newCall(httpRequest).execute().use { httpResponse ->
                                if (httpResponse.code != 200) {
                                    failedEmojis.add(emoji)
                                }
                            }

                            untilId = emoji.id
                        }
                    } catch (e: JsonProcessingException) {
                        MisskeyAdminTools.getInstance().moduleLogger.error(
                            "An error occurred while parsing the emoji.",
                            e
                        )
                    } catch (e: IOException) {
                        MisskeyAdminTools.getInstance().moduleLogger.error(
                            "An error occurred while checking the emoji.",
                            e
                        )
                    }
                }

                override fun onFailure(response: ApiResponse?) {
                    MisskeyAdminTools.getInstance().moduleLogger.error(
                        "An error occurred while checking the emoji.",
                        response!!.body
                    )
                }
            }).join()
        } while (count == 50)

        if (failedEmojis.isEmpty()) {
            context.channel.sendMessage("絵文字ファイルのチェックが完了しました。").queue()
        } else {
            context.channel.sendMessage("絵文字ファイルのチェックが完了しました。").queue()
            context.channel.sendMessage("以下の絵文字のチェックに失敗しました。").queue()

            val builder = StringBuilder("```")
            failedEmojis.forEach { emoji ->
                builder.append(emoji.name)
                builder.append("\n")
            }
            builder.append("```")
            context.channel.sendMessage(builder.toString()).queue()
        }
    }

    override fun getDescription(): String {
        return "絵文字ファイルのチェックを行います。"
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
