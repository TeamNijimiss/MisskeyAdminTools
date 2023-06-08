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
package app.nijimiss.mat.core.function

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
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.apache.commons.lang3.StringUtils
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.awt.Color
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ReportWatcher(
    private val systemStore: MATSystemDataStore,
    private val reportStore: ReportsStore,
    private val requestManager: ApiRequestManager,
    private val warningSender: WarningSender,
    private val token: String,
    private val silenceRoleId: String,
    private val targetReportChannel: Long,
    excludeRoleId: List<Long>?
) : ListenerAdapter() {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val discordApi: JDA = MisskeyAdminTools.getInstance().jda
    private val executorService: ScheduledExecutorService
    private val excludeRoleId: List<Long>

    init {
        this.excludeRoleId = excludeRoleId ?: emptyList()
        discordApi.addEventListener(this) // Add Button Interaction Listener
        discordApi.addEventListener(AutoClosure(token, requestManager, reportStore)) // Add Auto Closure
        executorService = Executors.newSingleThreadScheduledExecutor()
        executorService.scheduleAtFixedRate({ execute() }, 0, 5, TimeUnit.MINUTES)
    }

    fun shutdown() {
        executorService.shutdownNow()
        try {
            executorService.awaitTermination(1, TimeUnit.MINUTES)
        } catch (e: InterruptedException) {
            logger.error("An interruption occurred while waiting for the end.", e)
        }
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
                        val reportNotes = if (report.comment != null) NOTE_URL_PATTERN.matcher(report.comment) else null
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
                        val controlButtons = listOf(
                            Button.danger("report_freeze_" + report.targetUserID, "凍結 / Freeze"),
                            Button.secondary("report_silence_" + report.targetUserID, "ミュート / Silence"),
                            Button.success("report_completed_" + report.targetUserID, "完了 / Completed")
                        )
                        discordApi.getTextChannelById(targetReportChannel)
                            ?.sendMessageEmbeds(embedBuilder.build())
                            ?.addActionRow(controlButtons)?.queue { result ->
                                logger.debug("Report message sent. ID: " + result.id)
                                val messageId = result.id
                                val reportContext = ReportContext(
                                    report.id!!,
                                    messageId,
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

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val args = event.componentId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // report_freeze_1234567890: args[0] = "report", args[1] = action, args[2] = processId
        if (args[0] != "report") // If the button is not a report button, return.
            return
        val processId = args[2] // report target user id or report embed message id
        val action = args[1]
        val extendInfo = if (args.size > 3) args.copyOfRange(3, args.size) else null

        when (action) {
            "freeze" -> {
                if (excludeRoleId.stream().anyMatch { o: Long ->
                        event.member!!
                            .roles.map { role: Role -> role.idLong }
                            .contains(o)
                    }) {
                    event.reply(
                        """
                        あなたはこのユーザーを凍結する権限を持っていません。
                        You do not have permission to freeze this user.
                        """.trimIndent()
                    ).setEphemeral(true).queue()
                    return
                }
                event.reply(
                    """
                    ユーザーを凍結します。本当によろしいですか？
                    Are you sure you want to freeze the user?
                    """.trimIndent()
                )
                    .addActionRow(
                        Button.danger("report_confirm_" + processId + "_" + event.messageId, "凍結 / Freeze"),
                        Button.secondary("report_cancel_" + event.messageId, "キャンセル / Cancel")
                    ).setEphemeral(true).queue()
            }

            "silence" -> {
                val assign = Assign(token, processId, silenceRoleId)
                requestManager.addRequest(assign, object : ApiResponseHandler {
                    override fun onSuccess(response: ApiResponse?) {
                        val reportId =
                            event.message.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()[1].trim { it <= ' ' }

                        // Edit Report Embed
                        val embedBuilder = EmbedBuilder(event.message.embeds[0])
                        embedBuilder.setColor(Color.getHSBColor(0.50f, 0.82f, 0.45f))
                        embedBuilder.addField("処理 / Process", "ミュート / Silence", true)
                        embedBuilder.addField("処理者 / Processor", event.user.asTag, true)
                        embedBuilder.addField(
                            "処理日時 / Processed Date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(
                                Date()
                            ), true
                        )
                        event.message.editMessageEmbeds(embedBuilder.build()).queue()

                        // Resolve Report
                        val resolveAbuseUserReport = ResolveAbuseUserReport(token, reportId)
                        requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                            override fun onSuccess(response: ApiResponse?) {
                                // Reply
                                event.reply(
                                    """
                                    ミュートを行いました。
                                    The user has been silenced.
                                    """.trimIndent()
                                ).setEphemeral(true).queue()
                                // Remove Buttons
                                event.message.editMessageComponents().queue()

                                event.hook.sendMessage(
                                    """
                                    同一の投稿に関する通報を自動的にクローズしますか？
                                    Do you want to automatically close the report on the same post?
                                    """.trimIndent()
                                ).addActionRow(
                                    Button.success(
                                        "closure_close_${event.message.id}",
                                        "はい / Yes"
                                    ),
                                    Button.secondary(
                                        "closure_cancel_${event.message.id}",
                                        "いいえ / No"
                                    )
                                ).setEphemeral(true).queue()
                            }

                            override fun onFailure(response: ApiResponse?) {
                                event.reply(
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
                        event.reply(
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

            "completed" -> {
                event.reply(
                    """
                    完了するにあたって行ったアクションを選択してください。
                    Please select the action you took to complete the report.
                    """.trimIndent()
                )
                    .addActionRow(
                        Button.danger("report_warning_" + event.messageId, "警告 / Warning"),
                        Button.secondary("report_duplicate_" + event.messageId, "重複 / Duplicate"),
                        Button.secondary("report_done_" + event.messageId, "手動にて対応済み / Done manually"),
                        Button.primary("report_problem_" + event.messageId, "問題なし / No problem"),
                        Button.success("report_invalid_" + event.messageId, "無効 / Invalid")
                    )
                    .setEphemeral(true).queue()
            }

            "confirm" -> {
                event.channel.retrieveMessageById(extendInfo?.get(0) ?: processId).queue { msg: Message ->
                    if (msg.embeds.isEmpty()) return@queue

                    val reportId =
                        msg.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()[1].trim { it <= ' ' }
                    val suspendUser = SuspendUser(token, processId)
                    requestManager.addRequest(suspendUser, object : ApiResponseHandler {
                        override fun onSuccess(response: ApiResponse?) {
                            // Edit Report Embed
                            val embedBuilder = EmbedBuilder(msg.embeds[0])
                            embedBuilder.setColor(Color.getHSBColor(0.50f, 0.82f, 0.45f))
                            embedBuilder.addField("処理 / Process", "凍結 / Freeze", true)
                            embedBuilder.addField("処理者 / Processor", event.user.asTag, true)
                            embedBuilder.addField(
                                "処理日時 / Processed Date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(
                                    Date()
                                ), true
                            )
                            msg.editMessageEmbeds(embedBuilder.build()).queue()

                            // Resolve Report
                            event.deferReply(true).queue() // 処理に3秒以上かかる場合、Discord側でエラーが発生するため、応答を遅らせる
                            val resolveAbuseUserReport = ResolveAbuseUserReport(token, reportId)
                            requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                                override fun onSuccess(response: ApiResponse?) {
                                    event.hook.sendMessage(
                                        """
                                        凍結済みとして登録しました。
                                        The user has been registered as a frozen.
                                        """.trimIndent()
                                    ).queue()

                                    // Remove Buttons
                                    msg.editMessageComponents().queue()

                                    event.hook.sendMessage(
                                        """
                                        同一の投稿に関する通報を自動的にクローズしますか？
                                        Do you want to automatically close the report on the same post?
                                        """.trimIndent()
                                    ).addActionRow(
                                        Button.success(
                                            "closure_close_${msg.id}",
                                            "はい / Yes"
                                        ),
                                        Button.secondary(
                                            "closure_cancel_${msg.id}",
                                            "いいえ / No"
                                        )
                                    ).setEphemeral(true).queue()
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
                                    msg.editMessageComponents().queue()
                                }
                            })
                        }

                        override fun onFailure(response: ApiResponse?) {
                            event.reply(
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
            }

            "warning" -> {
                event.channel.retrieveMessageById(processId).queue { msg: Message ->
                    if (msg.embeds.isEmpty()) return@queue

                    // Resolve Report
                    val reportId = msg.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }

                    val targetUserId =
                        msg.embeds[0].fields[1].value!!.trim { it <= ' ' }

                    /* todo 通報された投稿を取得する 実装前のEmbedで取得できないため互換性保持のために本文から再度パースするコードを追加している。
                    val targetNotes = msg.embeds[0].fields.filter { it.name == "通報された投稿 / Reported Note" }
                        .mapNotNull { it.value }
                        .toList()
                     */

                    val targetNotes = NOTE_URL_PATTERN.matcher(msg.embeds[0].description).results().map { it.group(2) }
                        .filter(String::isNotBlank).toList()

                    // SQLから通報情報を取得する。SQLにデータがない場合は旧来の方法で生成する。
                    val context = reportStore.getReport(msg.idLong) ?: ReportContext(
                        reportId,
                        msg.id,
                        targetUserId,
                        targetNotes
                    )
                    warningSender.sendWarning(event, context)
                }
            }

            "duplicate" -> {
                event.channel.retrieveMessageById(processId).queue { msg: Message ->
                    if (msg.embeds.isEmpty()) return@queue

                    // Resolve Report
                    val reportId = msg.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }
                    val resolveAbuseUserReport = ResolveAbuseUserReport(token, reportId)
                    requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                        override fun onSuccess(response: ApiResponse?) {
                            event.reply(
                                """
                                重複通報として登録しました。
                                Registered as a duplicate report.
                                """.trimIndent()
                            ).setEphemeral(true).queue()
                            msg.delete().queue {
                                reportStore.removeReport(msg.idLong)
                            }
                        }

                        override fun onFailure(response: ApiResponse?) {
                            event.reply(
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

            "done" -> {
                event.channel.retrieveMessageById(processId).queue { msg: Message ->
                    if (msg.embeds.isEmpty()) return@queue

                    // Get Report ID
                    val reportId = msg.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }

                    // Edit Report Embed
                    val embedBuilder = EmbedBuilder(msg.embeds[0])
                    embedBuilder.setColor(Color.getHSBColor(0.50f, 0.82f, 0.45f))
                    embedBuilder.addField("処理 / Process", "手動にて対応済み / Done manually", true)
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
                            event.reply(
                                """
                                手動にて対応済みとして登録しました。
                                Registered as done manually.
                                """.trimIndent()
                            ).setEphemeral(true).queue()

                            // Remove buttons
                            msg.editMessageComponents().queue {
                                reportStore.removeReport(msg.idLong)
                            }
                        }

                        override fun onFailure(response: ApiResponse?) {
                            event.reply(
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
                            event.reply(
                                """
                                問題なしとして登録しました。
                                Registered as no problem.
                                """.trimIndent()
                            ).setEphemeral(true).queue()

                            // Remove buttons
                            msg.editMessageComponents().queue {
                                reportStore.removeReport(msg.idLong)
                            }
                        }

                        override fun onFailure(response: ApiResponse?) {
                            event.reply(
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

            "invalid" -> {
                event.channel.retrieveMessageById(processId).queue { msg: Message ->
                    if (msg.embeds.isEmpty()) return@queue

                    // Resolve Report
                    val reportId = msg.embeds[0].footer!!.text!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1].trim { it <= ' ' }
                    val resolveAbuseUserReport = ResolveAbuseUserReport(token, reportId)
                    requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                        override fun onSuccess(response: ApiResponse?) {
                            event.reply(
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
                            event.reply(
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

            "cancel" -> {
                event.reply(
                    """
                    キャンセルしました。
                    Canceled.
                    """.trimIndent()
                ).setEphemeral(true).queue()
            }

            else -> {
                event.reply("An error occurred while processing the button.").setEphemeral(true).queue()
            }
        }
    }

    companion object {
        private val MAPPER = ObjectMapper()
        private val NOTE_URL_PATTERN =
            Pattern.compile("https://([a-zA-Z0-9]+\\.[a-zA-Z0-9]+)/notes/([a-zA-Z0-9]+)")
    }
}
