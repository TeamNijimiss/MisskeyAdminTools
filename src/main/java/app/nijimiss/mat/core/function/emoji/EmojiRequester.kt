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
import app.nijimiss.mat.core.database.AccountsStore
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

class EmojiRequester(
    private val accountsStore: AccountsStore,
    private val emojiStore: EmojiStore,
    private val requestManager: ApiRequestManager,
) : CommandExecutor("emoji") {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val emojiManagerConfig: EmojiManagerConfig
    private val emojiManager: EmojiManager
    private val requesterHandler: MutableList<RequesterHandler> = mutableListOf()
    private val updateWaitlist: MutableMap<UUID, EmojiRequest> = mutableMapOf()


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

        emojiManager = EmojiManager(emojiManagerConfig, emojiStore, requestManager)

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
                        OptionType.STRING,
                        "description",
                        "絵文字の説明 / Description of emoji",
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
                        "tag",
                        "絵文字のタグ / Tag of emoji",
                        false,
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
                val tag = (context.options["tag"]?.value as String?)?.split(" ") ?: emptyList()
                val license = context.options["license"]?.value as String?
                val isSensitive = context.options["sensitive"]?.value as Boolean? ?: false

                // check account linked
                if (accountsStore.getMisskeyId(context.invoker.idLong) == null) {
                    context.responseSender.sendMessage("アカウントが連携されていません。 / Account is not linked.")
                        .setEphemeral(true).queue()
                    return
                }

                // name pattern check
                if (!name.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                    context.responseSender.sendMessage("絵文字の名前には半角英数字とアンダースコアのみ使用できます。 / Only half-width alphanumeric characters and underscores can be used in the name of the emoji.")
                        .setEphemeral(true).queue()
                    return
                }

                // name length check
                if (name.length > 32) {
                    context.responseSender.sendMessage("絵文字の名前は32文字以内で入力してください。 / Please enter the name of the emoji within 32 characters.")
                        .setEphemeral(true).queue()
                    return
                }

                // ailiases length check
                if (tag.joinToString(",").length > 127) {
                    context.responseSender.sendMessage("絵文字のタグは127文字以内で入力してください。 / Please enter the tag of the emoji within 127 characters.")
                        .setEphemeral(true).queue()
                    return
                }

                // license length check
                if ((license?.length ?: 0) > 127) {
                    context.responseSender.sendMessage("絵文字のライセンスは127文字以内で入力してください。 / Please enter the license of the emoji within 127 characters.")
                        .setEphemeral(true).queue()
                    return
                }

                // comment length check
                if (description.length > 256) {
                    context.responseSender.sendMessage("絵文字の説明は256文字以内で入力してください。 / Please enter the description of the emoji within 256 characters.")
                        .setEphemeral(true).queue()
                    return
                }

                // check request limit per month
                val requestCount = emojiStore.countEmojiRequestLastMonth(context.invoker.idLong)
                if (requestCount >= getRequestLimit(context.invoker)) {
                    context.responseSender.sendMessage("絵文字のリクエスト数が上限に達しています。 / The number of emoji requests has reached the limit.")
                        .setEphemeral(true).queue()
                    return
                }

                // check exist emoji
                val existsEmoji = emojiManager.getEmoji(name)
                if (existsEmoji != null) {
                    /*when (existsEmoji) {
                        is RegisteredEmoji -> {
                            context.responseSender.sendMessage("既に同じ名前の絵文字が存在します。 / Emoji with the same name already exists.")
                                .setEphemeral(true).queue()
                            return
                        }

                        is EmojiRequest -> {
                            context.responseSender.sendMessage("既に同じ名前の絵文字がリクエスト中です。 / Emoji with the same name is already requested.")
                                .setEphemeral(true).queue()
                            return
                        }

                        is ApprovedEmoji -> {
                            if (existsEmoji.requesterId == context.invoker.idLong) {
                                context.responseSender.sendMessage(
                                    "あなたが作成した同じ名前の絵文字が存在します。\n絵文字の内容を更新しますか？ " +
                                            "/ There is an emoji with the same name that you created.\nDo you want to update the contents of the emoji?"
                                )
                                    .setEphemeral(true).queue {
                                        val buttons = listOf(
                                            Button.primary("emoji_update_${existsEmoji.requestId}", "はい / Yes"),
                                            Button.danger("emoji_cancel_${existsEmoji.requestId}", "いいえ / No")
                                        )
                                        it.editMessageComponents().setActionRow(buttons).queue()
                                    }

                                updateWaitlist[existsEmoji.requestId] = EmojiRequest(
                                    existsEmoji.requestId,
                                    existsEmoji.requesterId,
                                    name,
                                    existsEmoji.imageFileId,
                                    existsEmoji.imageUrl,
                                    existsEmoji.license,
                                    existsEmoji.sensitive,
                                    existsEmoji.localOnly,
                                    existsEmoji.comment,
                                    existsEmoji.createdAt
                                )
                            }

                            return
                        }
                    }*/

                    context.responseSender.sendMessage("既に同じ名前の絵文字が存在します。 / Emoji with the same name already exists.")
                        .setEphemeral(true).queue()
                    return
                }


                // Upload emoji image file to Misskey
                uploadImage(image).let { uploadedFile ->
                    if (uploadedFile[0] == null || uploadedFile[1] == null) {
                        context.responseSender.sendMessage("絵文字のリクエストに失敗しました。 / Failed to request emoji.")
                            .setEphemeral(true).queue()
                        return
                    }

                    requesterHandler.forEach {
                        it.requestEmoji(
                            UUID.randomUUID(),
                            context.invoker.idLong,
                            name,
                            uploadedFile[0]!!,
                            uploadedFile[1]!!,
                            tag.toTypedArray(),
                            license,
                            isSensitive,
                            description
                        )
                    }

                    context.responseSender.sendMessage("絵文字のリクエストが完了しました。 / Emoji request completed.")
                        .setEphemeral(true).queue()
                }
            }

            // Sub command "request" description
            override fun getDescription(): String {
                return "絵文字をリクエストします。 / Request emoji."
            }
        })

        options.add(EmojiFileChecker(requestManager))
        options.add(EmojiRepairTool(emojiManagerConfig, requestManager))

        registerHandler(EmojiRequestReportSender(accountsStore, emojiStore, emojiManagerConfig.targetReportChannel))
        MisskeyAdminTools.getInstance().jda.addEventListener(
            EmojiRequestButtonHandler(
                accountsStore,
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

    private fun uploadImage(image: Attachment): Array<String?> {
        val uploadedFileId: Array<String?> = arrayOfNulls(2)

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
                if (!(uploadedFile.url == null || uploadedFile.id == null)) {
                    uploadedFileId[0] = uploadedFile.id
                    uploadedFileId[1] = uploadedFile.url
                }
            }

            override fun onFailure(response: ApiResponse?) {
                logger.error("Failed to upload image.\n{}: {}", response!!.statusCode, response.body)
            }
        }).join()

        // clean up temporary files
        file.delete()

        return uploadedFileId
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
