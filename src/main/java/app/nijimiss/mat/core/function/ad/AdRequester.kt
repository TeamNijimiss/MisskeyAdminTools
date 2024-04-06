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

package app.nijimiss.mat.core.function.ad

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.drive.files.Create
import app.nijimiss.mat.database.AccountsStore
import app.nijimiss.mat.database.AdStore
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.entities.Message
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
import java.text.SimpleDateFormat
import java.util.*

class AdRequester(
    private val accountsStore: AccountsStore,
    private val adStore: AdStore,
    private val requestManager: ApiRequestManager,
) : CommandExecutor("ads") {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val adManagerConfig: AdManagerConfig
    private val requesterHandler: MutableList<AdsRequesterHandler> = mutableListOf()

    init {
        val configFile = File(MisskeyAdminTools.getInstance().dataFolder, "AdManagerConfig.yaml")
        if (!configFile.exists()) {
            try {
                MisskeyAdminTools.getInstance().getResources("AdManagerConfig.yaml").use { original ->
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
        adManagerConfig = ConfigLoader.loadConfig(configFile, AdManagerConfig::class.java)

        options.add(object : SubCommandOption("request") {
            init {
                options.add(
                    CommandValueOption(
                        OptionType.STRING,
                        "description",
                        "広告の説明 / Description of ads",
                        true,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.ATTACHMENT,
                        "image",
                        "広告の画像 / Image of ads",
                        true,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.STRING,
                        "link",
                        "広告のリンク / Link of ads",
                        true,
                        false
                    )
                )
                options.add(
                    CommandValueOption(
                        OptionType.STRING,
                        "end_at",
                        "広告の終了日時 (yyyy-MM-dd HH:mm) / End date and time of ads (yyyy-MM-dd HH:mm)",
                        false,
                        false
                    )
                )
            }

            // Sub command "request" executor
            override fun onInvoke(context: CommandContext) {
                val description = context.options["description"]?.value as String
                val image = context.options["image"]?.value as Message.Attachment
                val link = context.options["link"]?.value as String
                val endAt = context.options["end_at"]?.value as String?

                if (context.invoker.roles.none { adManagerConfig.canRequestAdRoles.contains(it.idLong) }) {
                    context.responseSender.sendMessage(
                        """
                        広告を出稿する権限がありません。
                        You do not have permission to request ads.
                        """.trimIndent()
                    ).queue()
                    return
                }

                // リンクが正しいURL形式かを確認する
                if (!link.startsWith("http://") || !link.startsWith("https://")) {
                    context.responseSender.sendMessage(
                        """
                        リンクは正しいURL形式で設定してください。
                        Please set the link in the correct URL format.
                        """.trimIndent()
                    ).queue()
                    return
                }

                // 終了日が現在から1ヶ月後までの範囲に設定されているかを確認する
                var endAtDate: Date? = null
                if (endAt != null) {
                    try {
                        endAtDate = simpleDateFormat.parse(endAt)
                        if (endAtDate.time < System.currentTimeMillis() || endAtDate.time > System.currentTimeMillis() + 2592000000) {
                            context.responseSender.sendMessage(
                                """
                                終了日時は現在から1ヶ月後までの範囲で設定してください。
                                Please set the end date and time within 1 month from now.
                                """.trimIndent()
                            ).queue()
                            return
                        }
                    } catch (e: Exception) {
                        context.responseSender.sendMessage(
                            """
                            終了日時の形式が正しくありません。
                            The format of the end date and time is incorrect.
                            """.trimIndent()
                        ).queue()
                        return
                    }
                }

                // リクエスト者が既に広告をリクエストしているかを確認する
                if (adStore.checkActiveAd(context.invoker.idLong)) {
                    context.responseSender.sendMessage(
                        """
                        既に広告をリクエストしています。
                        You have already requested an ad.
                        """.trimIndent()
                    ).queue()
                    return
                }

                // ファイルをアップロード
                val uploadedFile = uploadImage(image)

                // リクエストを送信
                val requestId = UUID.randomUUID().toString()
                requesterHandler.forEach {
                    it.requestAds(
                        requestId,
                        context.invoker.idLong,
                        uploadedFile[0]!!,
                        uploadedFile[1]!!,
                        link,
                        description,
                        endAtDate?.time
                    )
                }
            }

            // Sub command "request" description
            override fun getDescription(): String {
                return "広告を出稿します。 / Request ads."
            }
        })

        requesterHandler.add(AdsRequestReportSender(accountsStore, adStore, adManagerConfig.targetReportChannel))
        MisskeyAdminTools.getInstance().jda.addEventListener(
            AdsRequestButtonHandler(
                accountsStore,
                adStore,
                requestManager,
            )
        )
    }

    override fun onInvoke(p0: CommandContext) {
        // Do nothing
    }

    override fun getDescription(): String {
        return "広告を出稿します。 / Request ads."
    }

    private fun uploadImage(image: Message.Attachment): Array<String?> {
        val uploadedFileId: Array<String?> = arrayOfNulls(2)

        val file = File(MisskeyAdminTools.getInstance().dataFolder, "ads/${image.fileName}")
        FileUtils.copyURLToFile(URL(image.url), file)
        val upload = Create(
            adManagerConfig.imageSaveFolderId.ifEmpty { null },
            image.fileName,
            null,
            false,
            file
        )
        requestManager.addRequest(upload, object : ApiResponseHandler {
            override fun onSuccess(response: ApiResponse?) {
                val uploadedFile = MAPPER.readValue(response!!.body, app.nijimiss.mat.entities.File::class.java)
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
        private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
    }
}
