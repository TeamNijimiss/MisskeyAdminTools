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

package app.nijimiss.mat.core.function.decoration

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.core.function.common.RequestHandler
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.drive.files.Create
import app.nijimiss.mat.database.AccountsStore
import app.nijimiss.mat.database.DecorationStore
import com.fasterxml.jackson.databind.ObjectMapper
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

class DecorationRequester(
    private val accountsStore: AccountsStore,
    private val decorationStore: DecorationStore,
    private val requestManager: ApiRequestManager,
) : CommandExecutor("decoration") {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val decorationManagerConfig: DecorationManagerConfig
    private val requesterHandler: MutableList<RequestHandler> = mutableListOf()

    init {
        val configFile = File(MisskeyAdminTools.getInstance().dataFolder, "DecorationManagerConfig.yaml")
        if (!configFile.exists()) {
            try {
                MisskeyAdminTools.getInstance().getResources("DecorationManagerConfig.yaml").use { original ->
                    Files.copy(original, configFile.toPath())
                    logger.info("The configuration file was not found, so a new file was created.")
                }
            } catch (e: IOException) {
                logger.error("Failed to create a new configuration file.", e)
            }
        }
        decorationManagerConfig = ConfigLoader.loadConfig(configFile, DecorationManagerConfig::class.java)

        options.add(object : SubCommandOption("request") {
            init {
                options.add(
                    CommandValueOption(
                        OptionType.STRING,
                        "name",
                        "装飾の名前 / Name of decoration",
                        true,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.STRING,
                        "description",
                        "装飾の説明 / Description of decoration",
                        true,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.ATTACHMENT,
                        "image",
                        "装飾の画像 / Image of decoration",
                        true,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.STRING,
                        "license",
                        "装飾のライセンス / License of decoration",
                        false,
                        false
                    )
                )
            }

            // Sub command "request" executor
            override fun onInvoke(context: CommandContext) {
                val name = context.options["name"]?.value as String
                val description = context.options["description"]?.value as String
                val image = context.options["image"]?.value as Attachment
                val license = context.options["license"]?.value as String?

                // name pattern check
                if (!name.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                    context.responseSender.sendMessage("装飾の名前には半角英数字とアンダースコアのみ使用できます。 / Only alphanumeric characters and underscores can be used in the name of the decoration.")
                        .setEphemeral(true).queue()
                    return
                }

                // name length check
                if (name.length > 32) {
                    context.responseSender.sendMessage("装飾の名前は32文字以内で入力してください。 / Please enter the name of the decoration within 32 characters.")
                        .setEphemeral(true).queue()
                    return
                }

                // license length check
                if ((license?.length ?: 0) > 127) {
                    context.responseSender.sendMessage("装飾のライセンスは127文字以内で入力してください。 / Please enter the decoration license within 127 characters.")
                        .setEphemeral(true).queue()
                    return
                }

                // check request limit per month
                val requestCount = decorationStore.countDecorationRequestsThisMonth(context.invoker.idLong)
                if (requestCount >= (decorationManagerConfig.roles.find { it.discordRole == context.invoker.roles[0].idLong }?.limit
                        ?: decorationManagerConfig.baseLimit)
                ) {
                    context.responseSender.sendMessage("デコレーションのリクエスト数が上限に達しています。 / The number of decoration requests has reached the limit.")
                        .setEphemeral(true).queue()
                    return
                }

                // ファイルをアップロード
                val uploadedFile = uploadImage(image)

                // リクエストを送信
                val requestId = UUID.randomUUID()
                requesterHandler.forEach {
                    it.requestCreate(
                        DecorationRequest(
                            requestId,
                            context.invoker.idLong,
                            name,
                            uploadedFile[0]!!,
                            uploadedFile[1]!!,
                            license,
                            description,
                            System.currentTimeMillis()
                        )
                    )
                }
            }

            // Sub command "request" description
            override fun getDescription(): String {
                return "装飾をリクエストします。 / Request a decoration."
            }
        })

        requesterHandler.add(
            DecorationRequestReportSender(
                accountsStore,
                decorationStore,
                decorationManagerConfig.targetReportChannel
            )
        )
        MisskeyAdminTools.getInstance().jda.addEventListener(
            DecorationButtonHandler(
                accountsStore,
                decorationStore,
                requestManager,
            )
        )
    }

    private fun uploadImage(image: Attachment): Array<String?> {
        val uploadedFileId: Array<String?> = arrayOfNulls(2)

        val file = File(MisskeyAdminTools.getInstance().dataFolder, "decoration/${image.fileName}")
        FileUtils.copyURLToFile(URL(image.url), file)
        val upload = Create(
            decorationManagerConfig.imageSaveFolderId.ifEmpty { null },
            image.fileName,
            null,
            false,
            file
        )
        requestManager.addRequest(upload, object : ApiResponseHandler {
            override fun onSuccess(response: ApiResponse?) {
                val uploadedFile =
                    MAPPER.readValue(response!!.body, app.nijimiss.mat.entities.File::class.java)
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


    override fun onInvoke(p0: CommandContext) {
        // Do nothing
    }

    override fun getDescription(): String {
        return "装飾関連のコマンドです。 / Commands related to decorations."
    }

    companion object {
        val MAPPER = ObjectMapper()
    }
}
