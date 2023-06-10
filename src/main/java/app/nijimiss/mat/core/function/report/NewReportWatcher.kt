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

package app.nijimiss.mat.core.function.report

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.api.misskey.admin.Report
import app.nijimiss.mat.core.database.MATSystemDataStore
import app.nijimiss.mat.core.database.ReportsStore
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.AbuseUserReports
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.ResolveAbuseUserReport
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.SuspendUser
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.roles.Assign
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.apache.commons.lang3.StringUtils
import page.nafuchoco.neobot.api.ConfigLoader
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.awt.Color
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class NewReportWatcher(
    private val systemStore: MATSystemDataStore,
    private val reportStore: ReportsStore,
    private val requestManager: ApiRequestManager,
    private val token: String,
) : ListenerAdapter() {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val discordApi: JDA = MisskeyAdminTools.getInstance().jda
    private val watcherConfig: ReportWatcherConfig
    private val executorService: ScheduledExecutorService
    private val targetReportChannel: Long
    private val warningSender: WarningSender?
    private val silenceRoleId: String?
    private val excludeRoleId: List<Long>

    init {
        val configFile = File(MisskeyAdminTools.getInstance().dataFolder, "ReportWatcherConfig.yaml")
        if (!configFile.exists()) {
            try {
                MisskeyAdminTools.getInstance().getResources("ReportWatcherConfig.yaml").use { original ->
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
        watcherConfig = ConfigLoader.loadConfig(configFile, ReportWatcherConfig::class.java)

        warningSender =
            if (watcherConfig.warningSender?.warningTemplate != null && watcherConfig.warningSender.warningItems != null)
                WarningSender(
                    token,
                    requestManager,
                    watcherConfig.warningSender.warningTemplate,
                    watcherConfig.warningSender.warningItems
                ) else {
                null
            }
        if (warningSender == null) logger.warn("The warning sender is not set.")

        targetReportChannel =
            watcherConfig.targetReportChannel ?: throw IllegalStateException("The target report channel is not set.")
        silenceRoleId = watcherConfig.silenceRoleId
        excludeRoleId = watcherConfig.excludeDiscordRoles ?: emptyList()
        discordApi.addEventListener(this) // Add Button Interaction Listener
        discordApi.addEventListener(AutoClosure(token, requestManager, reportStore)) // Add Auto Closure
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService.scheduleAtFixedRate({ execute() }, 0, 5, TimeUnit.MINUTES)
    }

    private fun execute() {
        val sinceId: String? = try {
            systemStore.getOption("lastCheckedReport")
        } catch (e: SQLException) {
            MisskeyAdminTools.getInstance().moduleLogger
                .error("An error occurred while getting the last checked report.", e)
            return
        }

        val abuseUserReports = AbuseUserReports(token, 10, sinceId, null, "unresolved", "combined", "combined", false)
        requestManager.addRequest(abuseUserReports, object : ApiResponseHandler {
            override fun onSuccess(response: ApiResponse?) {
                try {
                    val reports = MAPPER.readValue(
                        response!!.body, object : TypeReference<List<Report>>() {})
                    if (sinceId == null) Collections.reverse(reports) // 実質的には不変ではない

                    for (report in reports) {
                        val reportNotes =
                            if (report.comment != null) NOTE_URL_PATTERN.matcher(report.comment) else null
                        val noteIds = reportNotes!!.results().map { it.group(2) }.toList()

                        val embedBuilder = EmbedBuilder()
                        embedBuilder.setTitle("通報 / Report")
                        embedBuilder.setColor(Color.getHSBColor(0.03f, 0.39f, 0.49f))
                        embedBuilder.setDescription(report.comment)
                        StringUtils.defaultIfEmpty(
                            report.reporter!!.username, "null"
                        )?.let {
                            embedBuilder.addField(
                                "通報者 / Reporter", it, true
                            )
                        }
                        StringUtils.defaultIfEmpty(
                            report.targetUser!!.username, "null"
                        )?.let {
                            embedBuilder.addField(
                                "通報されたユーザー / Reported User", it, true
                            )
                        }
                        StringUtils.defaultIfEmpty(
                            report.createdAt, "N/A"
                        )?.let {
                            embedBuilder.addField(
                                "通報された日時 / Reported Date", it, true
                            )
                        }
                        for (noteInfo in noteIds) {
                            embedBuilder.addField(
                                "通報された投稿 / Reported Note", noteInfo, true
                            )
                        }
                        embedBuilder.setFooter("通報 ID: " + report.id)


                        discordApi.getTextChannelById(targetReportChannel)
                            ?.sendMessageEmbeds(embedBuilder.build())
                            ?.queue { result ->
                                logger.debug("Report message sent. ID: " + result.id)


                                result.editMessageComponents(getMainMenu(report.id!!)).queue()

                                val reportContext = ReportContext(
                                    report.id,
                                    result.id,
                                    report.targetUserID!!,
                                    noteIds
                                )
                                try {
                                    reportStore.addReport(reportContext)
                                } catch (e: SQLException) {
                                    MisskeyAdminTools.getInstance().moduleLogger.error(
                                        "An error occurred while adding the report.", e
                                    )
                                }
                            }
                    }
                    if (reports.isNotEmpty()) systemStore.setOption("lastCheckedReport", reports[reports.size - 1].id)
                } catch (e: JsonProcessingException) {
                    MisskeyAdminTools.getInstance().moduleLogger.error("An error occurred while parsing the report.", e)
                } catch (e: SQLException) {
                    MisskeyAdminTools.getInstance().moduleLogger.error(
                        "An error occurred while updating the last checked report.",
                        e
                    )
                }
            }

            override fun onFailure(response: ApiResponse?) {
                MisskeyAdminTools.getInstance().moduleLogger.error(
                    """
                    An error occurred while getting the report.
                    Response Code: {}, Body: {}
                    """.trimIndent(), response!!.statusCode, response.body
                )
            }
        })
    }

    fun shutdown() {
        executorService.shutdownNow()
        try {
            executorService.awaitTermination(1, TimeUnit.MINUTES)
        } catch (e: InterruptedException) {
            logger.error("An interruption occurred while waiting for the end.", e)
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val args = event.componentId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // mod_freeze_1234567890: args[0] = "report", args[1] = action, args[2] = processId
        if (args[0] != "mod") // If the button is not a report button, return.
            return
        val processId = args[2] // report target user id or report embed message id
        val action = args[1]
        val extendInfo = if (args.size > 3) args.copyOfRange(3, args.size) else null

        event.deferEdit().queue()

        when (action) {
            "freeze" -> {
                if (excludeRoleId.stream().anyMatch { o: Long ->
                        event.member!!
                            .roles.map { role: Role -> role.idLong }
                            .contains(o)
                    }) {
                    event.hook.sendMessage(
                        """
                        あなたはこのユーザーを凍結する権限を持っていません。
                        You do not have permission to freeze this user.
                        """.trimIndent()
                    ).setEphemeral(true).queue()
                    return
                }

                val confirmButton = listOf(
                    Button.danger(
                        "mod_confirm_${processId}",
                        "本当に凍結を実行しますか？ / Are you sure you want to freeze?"
                    ),
                    Button.secondary("mod_main_${processId}", "キャンセル / Cancel")
                ).map { ActionRow.of(it) }
                event.message.editMessageComponents(confirmButton).queue()
            }

            "silence" -> {
                if (silenceRoleId == null) {
                    event.hook.sendMessage(
                        """
                        ミュート機能は無効になっています。
                        The mute function is disabled.
                        """.trimIndent()
                    ).setEphemeral(true).queue()
                    return
                }

                val assign = Assign(token, processId, silenceRoleId)
                requestManager.addRequest(assign, object : ApiResponseHandler {
                    override fun onSuccess(response: ApiResponse?) {
                        val reportId =
                            event.message.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()[1].trim { it <= ' ' }

                        // Edit Report Embed
                        addReportStatus(event.message, "ミュート済み / Muted", event.user.asTag)

                        // Resolve Report
                        val resolveAbuseUserReport = ResolveAbuseUserReport(token, reportId)
                        requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                            override fun onSuccess(response: ApiResponse?) {
                                val confirmButton = listOf(
                                    Button.danger(
                                        "closure_close_${event.messageId}",
                                        "同一投稿への通報を自動的にクローズしますか？ / Do you want to automatically close the report on the same post?"
                                    ),
                                    Button.secondary("mod_clear_${processId}", "キャンセル / Cancel")
                                ).map { ActionRow.of(it) }
                                event.message.editMessageComponents(confirmButton).queue()
                            }

                            override fun onFailure(response: ApiResponse?) {
                                event.hook.sendMessage(
                                    """
                                    通報のクローズに失敗しました。ミュート処理は完了しています。手動で通報をクローズしてください。
                                    Report close failed. The silence process has been completed. Please close the report manually.
                                    """.trimIndent()
                                ).setEphemeral(true).queue()

                                MisskeyAdminTools.getInstance().moduleLogger.error(
                                    """
                                    An error occurred while closing the report.
                                    Response Code: {}, Body: {}
                                    """.trimIndent(), response!!.statusCode, response.body
                                )
                                // Remove Buttons
                                event.message.editMessageComponents().queue()
                            }
                        })
                    }

                    override fun onFailure(response: ApiResponse?) {
                        event.hook.sendMessage(
                            """
                            ミュートに失敗しました。時間を置いて実行してください。
                            Failed to silence the user. Please try again later.
                            """.trimIndent()
                        ).setEphemeral(true).queue()

                        MisskeyAdminTools.getInstance().moduleLogger.error(
                            """
                            An error occurred while silencing the user.
                            Response Code: {}, Body: {}
                            """.trimIndent(), response!!.statusCode, response.body
                        )
                    }
                })
            }

            "confirm" -> {
                val reportId =
                    event.message.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }
                val suspendUser = SuspendUser(token, processId)
                requestManager.addRequest(suspendUser, object : ApiResponseHandler {
                    override fun onSuccess(response: ApiResponse?) {
                        // Edit Report Embed
                        addReportStatus(event.message, "凍結済み / Frozen", event.user.asTag)

                        // Resolve Report
                        val resolveAbuseUserReport = ResolveAbuseUserReport(token, reportId)
                        requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                            override fun onSuccess(response: ApiResponse?) {
                                val confirmButton = listOf(
                                    Button.danger(
                                        "closure_close_${event.messageId}",
                                        "同一投稿への通報を自動的にクローズしますか？ / Do you want to automatically close the report on the same post?"
                                    ),
                                    Button.secondary("mod_clear_${processId}", "キャンセル / Cancel")
                                ).map { ActionRow.of(it) }
                                event.message.editMessageComponents(confirmButton).queue()
                            }

                            override fun onFailure(response: ApiResponse?) {
                                event.hook.sendMessage(
                                    """
                                        通報のクローズに失敗しました。凍結処理は完了しています。手動で通報をクローズしてください。
                                        Report close failed. The freeze process has been completed. Please close the report manually.
                                        """.trimIndent()
                                ).queue()

                                MisskeyAdminTools.getInstance().moduleLogger.error(
                                    """
                                        An error occurred while closing the report.
                                        Response Code: {}, Body: {}
                                        """.trimIndent(), response!!.statusCode, response.body
                                )

                                // Remove Buttons
                                event.message.editMessageComponents().queue()
                            }
                        })
                    }

                    override fun onFailure(response: ApiResponse?) {
                        event.hook.sendMessage(
                            """
                                凍結に失敗しました。時間を置いて実行してください。
                                Failed to freeze the user. Please try again later.
                                """.trimIndent()
                        ).setEphemeral(true).queue()

                        MisskeyAdminTools.getInstance().moduleLogger.error(
                            """
                                An error occurred while freezing the user.
                                Response Code: {}, Body: {}
                                """.trimIndent(), response!!.statusCode, response.body
                        )
                    }
                })
            }

            "warning" -> {
                if (warningSender == null) {
                    event.hook.sendMessage(
                        """
                        警告送信機能が無効になっています。
                        The warning sending function is disabled.
                        """.trimIndent()
                    ).setEphemeral(true).queue()
                    return
                }

                // Resolve Report
                val reportId =
                    event.message.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }

                val targetUserId =
                    event.message.embeds[0].fields[1].value!!.trim { it <= ' ' }

                val targetNotes =
                    NOTE_URL_PATTERN.matcher(event.message.embeds[0].description).results().map { it.group(2) }
                        .filter(String::isNotBlank).toList()

                // SQLから通報情報を取得する。SQLにデータがない場合は旧来の方法で生成する。
                val context = reportStore.getReport(event.message.idLong) ?: ReportContext(
                    reportId,
                    event.message.id,
                    targetUserId,
                    targetNotes
                )
                warningSender.sendWarning(event, context)
            }

            "problem" -> {
                event.channel.retrieveMessageById(processId).queue { msg: Message ->
                    if (msg.embeds.isEmpty()) return@queue

                    // Get Report ID
                    val reportId = msg.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }

                    // Edit Report Embed
                    val embedBuilder = EmbedBuilder(msg.embeds[0])
                    embedBuilder.setColor(Color.getHSBColor(0.50f, 0.82f, 0.45f))
                    embedBuilder.addField("処理 / Process", "問題なし / No problem", true)
                    embedBuilder.addField("処理者 / Processor", event.user.asTag, true)
                    embedBuilder.addField(
                        "処理日時 / Processed Date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(
                            Date()
                        ), true
                    )
                    msg.editMessageEmbeds(embedBuilder.build()).queue()

                    // Resolve Report
                    val resolveAbuseUserReport = ResolveAbuseUserReport(token, reportId)
                    requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                        override fun onSuccess(response: ApiResponse?) {
                            // Remove buttons
                            msg.editMessageComponents().queue {
                                reportStore.removeReport(msg.idLong)
                            }
                        }

                        override fun onFailure(response: ApiResponse?) {
                            event.hook.sendMessage(
                                """
                                通報のクローズに失敗しました。時間を置いて実行してください。
                                Report close failed. Please try again later.
                                """.trimIndent()
                            ).setEphemeral(true).queue()

                            MisskeyAdminTools.getInstance().moduleLogger.error(
                                """
                                An error occurred while closing the report.
                                Response Code: {}, Body: {}
                                """.trimIndent(), response!!.statusCode, response.body
                            )
                        }
                    })
                }
            }

            "noaction" -> {
                event.channel.retrieveMessageById(
                    if (processId == "0") event.messageId else processId
                ).queue { msg: Message ->
                    if (msg.embeds.isEmpty()) return@queue

                    // Resolve Report
                    val reportId = msg.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }
                    val resolveAbuseUserReport = ResolveAbuseUserReport(token, reportId)
                    requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                        override fun onSuccess(response: ApiResponse?) {
                            event.hook.sendMessage(
                                """
                                無効通報として登録しました。
                                Registered as an invalid report.
                                """.trimIndent()
                            ).setEphemeral(true).queue()
                            msg.delete().queue {
                                reportStore.removeReport(msg.idLong)
                            }
                        }

                        override fun onFailure(response: ApiResponse?) {
                            event.hook.sendMessage(
                                """
                                通報のクローズに失敗しました。時間を置いて実行してください。
                                Report close failed. Please try again later.
                                """.trimIndent()
                            ).setEphemeral(true).queue()

                            MisskeyAdminTools.getInstance().moduleLogger.error(
                                """
                                An error occurred while closing the report.
                                Response Code: {}, Body: {}
                                """.trimIndent(), response!!.statusCode, response.body
                            )
                        }
                    })
                }
            }

            "main" -> {
                event.message.editMessageComponents(getMainMenu(processId)).queue()
            }

            "clear" -> {
                event.message.editMessageComponents().queue()
            }

            else -> {
                event.reply("An error occurred while processing the button.").setEphemeral(true).queue()
            }
        }
    }

    private fun getMainMenu(reportId: String): List<ActionRow> {
        return listOf(
            Button.danger("mod_freeze_${reportId}", "凍結 / Freeze"),
            Button.danger("mod_warning_${reportId}", "警告 / Warning"),
            Button.primary("mod_silence_${reportId}", "ミュート / Silence"),
            Button.secondary(
                "mod_noaction_${reportId}",
                "重複・無効 / No Action"
            ), // why id 0? because it's not used
            Button.success("mod_problem_${reportId}", "問題なし / No problem"),
        ).map { ActionRow.of(it) }
    }

    private fun addReportStatus(msg: Message, status: String, processor: String) {
        val embedBuilder = EmbedBuilder(msg.embeds[0])
        embedBuilder.addField("処理 / Process", status, true)
        embedBuilder.addField("処理者 / Processor", processor, true)
        embedBuilder.addField(
            "処理日時 / Processed Date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(
                Date()
            ), true
        )
        msg.editMessageEmbeds(embedBuilder.build()).queue()
    }

    companion object {
        private val MAPPER = ObjectMapper()
        private val NOTE_URL_PATTERN =
            Pattern.compile("https://([a-zA-Z0-9]+\\.[a-zA-Z0-9]+)/notes/([a-zA-Z0-9]+)")
    }
}
