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
import app.nijimiss.mat.api.misskey.Emoji
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.emoji.Update
import app.nijimiss.mat.core.requests.misskey.endpoints.drive.files.Create
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import devcsrj.okhttp3.logging.HttpLoggingInterceptor
import net.dv8tion.jda.api.entities.Role
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import page.nafuchoco.neobot.api.command.CommandContext
import page.nafuchoco.neobot.api.command.SubCommandOption
import java.io.File
import java.io.IOException
import java.net.URL

class EmojiRepairTool(
    private val emojiManagerConfig: EmojiManagerConfig,
    private val requestManager: ApiRequestManager,
) : SubCommandOption("repair") {
    private val logging: HttpLoggingInterceptor = HttpLoggingInterceptor(LoggerFactory.getLogger(this.javaClass))
    private val httpClient: OkHttpClient = OkHttpClient.Builder().addInterceptor(logging).build()
    private val superUserRoleIds =
        MisskeyAdminTools.getInstance().config.authentication?.superUserRoleIds ?: emptyList()

    override fun onInvoke(context: CommandContext) {
        if (!superUserRoleIds.stream().anyMatch { o: Long ->
                context.invoker
                    .roles.map { role: Role -> role.idLong }
                    .contains(o)
            }) {
            context.responseSender.sendMessage(
                """
                        あなたはこのアクションを実行する権限を持っていません。
                        You do not have permission to perform this action.
                        """.trimIndent()
            ).setEphemeral(true).queue()
            return
        }

        context.responseSender.sendMessage("絵文字ファイルのチェックを開始します。").queue()
        val failedEmojis = mutableListOf<Emoji>()
        var untilId: String? = null
        do {
            var count = 0
            requestManager.addRequest(
                app.nijimiss.mat.core.requests.misskey.endpoints.admin.emoji.List(
                    null,
                    50,
                    null,
                    untilId
                ), object : ApiResponseHandler {
                    override fun onSuccess(response: ApiResponse?) {
                        try {
                            val emojis = MAPPER.readValue(
                                response!!.body, object : TypeReference<List<Emoji>>() {})

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
            context.channel.sendMessage("絵文字ファイルの修復を開始します。").queue()

            failedEmojis.forEach { emoji ->
                val extension = FilenameUtils.getExtension(URL(emoji.url).file)
                val file = File(MisskeyAdminTools.getInstance().dataFolder, "emoji/${emoji.name}.${extension}")
                if (file.exists()) {
                    val upload = Create(
                        emojiManagerConfig.imageSaveFolderId.ifEmpty { null },
                        file.name,
                        null,
                        false,
                        file
                    )

                    var uploadedFile: app.nijimiss.mat.api.misskey.File? = null
                    requestManager.addRequest(upload, object : ApiResponseHandler {
                        override fun onSuccess(response: ApiResponse?) {
                            MisskeyAdminTools.getInstance().moduleLogger.info(
                                "The emoji file has been uploaded. [id: ${emoji.id}, name: ${emoji.name}]"
                            )

                            uploadedFile =
                                MAPPER.readValue(response!!.body, app.nijimiss.mat.api.misskey.File::class.java)
                        }

                        override fun onFailure(response: ApiResponse?) {
                            MisskeyAdminTools.getInstance().moduleLogger.error(
                                "An error occurred while uploading the emoji file. [id: ${emoji.id}, name: ${emoji.name}]",
                                response!!.body
                            )
                        }

                    }).join()

                    if (uploadedFile == null || uploadedFile!!.id == null) {
                        MisskeyAdminTools.getInstance().moduleLogger.error(
                            "An error occurred while uploading the emoji file. [id: ${emoji.id}, name: ${emoji.name}]"
                        )
                        return
                    }

                    requestManager.addRequest(
                        Update(
                            emoji.id!!,
                            emoji.name!!,
                            emoji.aliases!!.toTypedArray(),
                            uploadedFile!!.id!!,
                            emoji.category,
                            emoji.license,
                            emoji.isSensitive!!,
                            emoji.localOnly!!,
                            emoji.requestedBy,
                            emoji.memo,
                            emoji.roleIDSThatCanBeUsedThisEmojiAsReaction!!.toTypedArray()
                        ),
                        object : ApiResponseHandler {
                            override fun onSuccess(response: ApiResponse?) {
                                MisskeyAdminTools.getInstance().moduleLogger.info(
                                    "The emoji file has been deleted. [id: ${emoji.id}, name: ${emoji.name}]"
                                )
                            }

                            override fun onFailure(response: ApiResponse?) {
                                MisskeyAdminTools.getInstance().moduleLogger.error(
                                    "An error occurred while deleting the emoji file. [id: ${emoji.id}, name: ${emoji.name}]",
                                    response!!.body
                                )
                            }
                        }).join()
                }
            }

            context.channel.sendMessage("絵文字ファイルの修復が完了しました。").queue()
        }
    }

    override fun getDescription(): String {
        return "破損した絵文字ファイルを修復します。"
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
