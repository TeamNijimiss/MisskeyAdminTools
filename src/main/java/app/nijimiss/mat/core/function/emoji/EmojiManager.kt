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
import app.nijimiss.mat.core.database.EmojiStore
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.drive.files.Create
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message.Attachment
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.apache.commons.io.FileUtils
import page.nafuchoco.neobot.api.ConfigLoader
import page.nafuchoco.neobot.api.command.CommandContext
import page.nafuchoco.neobot.api.command.CommandExecutor
import page.nafuchoco.neobot.api.command.CommandValueOption
import page.nafuchoco.neobot.api.command.SubCommandOption
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.util.*

class EmojiManager(
    private val emojiStore: EmojiStore,
    private val requestManager: ApiRequestManager,
) : CommandExecutor("emoji") {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val emojiManagerConfig: EmojiManagerConfig
    private val requesterHandler: MutableList<RequesterHandler> = mutableListOf()


    init {
        val configFile = File(MisskeyAdminTools.getInstance().dataFolder, "EmojiManagerConfig.yaml")
        if (!configFile.exists()) {
            try {
                MisskeyAdminTools.getInstance().getResources("EmojiManagerConfig.yaml").use { original ->
                    Files.copy(original, configFile.toPath())
                    logger.info("The configuration file was not found, so a new file was created.")
                }
            } catch (e: IOException) {
                logger.error(
                    """
                    The correct configuration file could not be retrieved from the executable.
                    If you have a series of problems, please contact the developer.
                    """.trimIndent(), e
                )
            }
        }
        emojiManagerConfig = ConfigLoader.loadConfig(configFile, EmojiManagerConfig::class.java)

        options.add(object : SubCommandOption("request") {
            init {
                options.add(
                    CommandValueOption(
                        OptionType.STRING,
                        "name",
                        "絵文字の名前 / Name of emoji",
                        true,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.ATTACHMENT,
                        "image",
                        "絵文字の画像 / Image of emoji",
                        true,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.STRING,
                        "description",
                        "絵文字の説明 / Description of emoji",
                        true,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.STRING,
                        "license",
                        "絵文字のライセンス / License of emoji",
                        false,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.BOOLEAN,
                        "sensitive",
                        "絵文字がNSFWかどうか / Whether the emoji is NSFW",
                        false,
                        false
                    )
                )
            }

            // Sub command "request" executor
            override fun onInvoke(context: CommandContext) {
                val name = context.options["name"]?.value as String
                val image = context.options["image"]?.value as Attachment
                val description = context.options["description"]?.value as String
                val license = context.options["license"]?.value as String?
                val isSensitive = context.options["sensitive"]?.value as Boolean? ?: false

                // check exist emoji
                if (emojiStore.existsEmoji(name)) {
                    context.responseSender.sendMessage("既に同じ名前の絵文字が存在します。 / Emoji with the same name already exists.")
                        .queue()
                    return
                }

                // check request limit per month
                val requestCount = emojiStore.countEmojiRequestLastMonth(context.invoker.idLong)
                if (requestCount >= getRequestLimit(context.invoker)) {
                    context.responseSender.sendMessage("絵文字のリクエスト数が上限に達しています。 / The number of emoji requests has reached the limit.")
                        .queue()
                    return
                }

                // Upload emoji image file to Misskey
                val file = File(MisskeyAdminTools.getInstance().dataFolder, "emoji/${image.fileName}")
                FileUtils.copyURLToFile(URL(image.url), file)
                val upload = Create(
                    emojiManagerConfig.imageSaveFolderId.ifEmpty { null },
                    image.fileName,
                    null,
                    false,
                    file
                )
                requestManager.addRequest(upload, object : ApiResponseHandler {
                    override fun onSuccess(response: ApiResponse?) {
                        val uploadedFile =
                            MAPPER.readValue(response!!.body, app.nijimiss.mat.api.misskey.File::class.java)
                        if (uploadedFile.url == null || uploadedFile.id == null) {
                            context.responseSender.sendMessage("絵文字のリクエストに失敗しました。 / Failed to request emoji.")
                                .queue()
                            return
                        }

                        requesterHandler.forEach {
                            it.requestEmoji(
                                UUID.randomUUID(),
                                context.invoker.idLong,
                                name,
                                uploadedFile.id,
                                uploadedFile.url,
                                license,
                                isSensitive,
                                description
                            )
                        }

                        context.responseSender.sendMessage("絵文字のリクエストが完了しました。 / Emoji request completed.")
                            .queue()
                    }

                    override fun onFailure(response: ApiResponse?) {
                        context.responseSender.sendMessage("絵文字のリクエストに失敗しました。 / Failed to request emoji.")
                            .queue()
                        logger.error("Failed to request emoji.\n{}: {}", response!!.statusCode, response.body)
                    }
                })
            }

            // Sub command "request" description
            override fun getDescription(): String {
                return "絵文字をリクエストします。 / Request emoji."
            }
        })

        registerHandler(EmojiRequestReportSender(emojiStore, emojiManagerConfig.targetReportChannel))
        MisskeyAdminTools.getInstance().jda.addEventListener(
            EmojiRequestButtonHandler(
                emojiStore,
                requestManager,
            )
        )
    }


    fun registerHandler(handler: RequesterHandler) {
        requesterHandler.add(handler)
    }

    override fun onInvoke(context: CommandContext) {
        // this method is not used.
    }

    override fun getDescription(): String {
        // this method is not used.
        return "絵文字を管理します。 / Manage emoji."
    }

    private fun getRequestLimit(requester: Member): Int {
        var limit = emojiManagerConfig.baseLimit
        emojiManagerConfig.roles.forEach { role ->
            if (requester.roles.any { it.idLong == role.discordRole }) limit = role.limit
        }
        return limit
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
