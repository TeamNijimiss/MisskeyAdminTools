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
package app.nijimiss.mat.core.function.report

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.api.misskey.FullUser
import app.nijimiss.mat.core.database.ReportsStore
import app.nijimiss.mat.core.database.UserStore
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.ResolveAbuseUserReport
import app.nijimiss.mat.core.requests.misskey.endpoints.notes.Create
import app.nijimiss.mat.core.requests.misskey.endpoints.notes.Create.Visibility
import app.nijimiss.mat.core.requests.misskey.endpoints.users.Show
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import java.awt.Color
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Consumer

class WarningSender(
    private val reportStore: ReportsStore,
    private val userStore: UserStore,
    private val requestManager: ApiRequestManager,
    private val warningTemplate: String,
    private val warningItems: List<String>,
    private val continuousWarningLimit: Int
) : ListenerAdapter() {
    private val reportContexts: MutableMap<String, ReportContext>
    private val warningContexts: MutableMap<String, ReportWarningContext>

    init {
        reportContexts = HashMap()
        warningContexts = HashMap()

        MisskeyAdminTools.getInstance().jda.addEventListener(this)
    }

    fun sendWarning(event: ButtonInteractionEvent, context: ReportContext) {
        val builder = StringSelectMenu.create("warning_reason_" + context.messageId)
        builder.setPlaceholder("警告の理由を選択します。 / Select a reason for the warning.")
        warningItems.forEach(Consumer { warningItem: String ->
            builder.addOption(
                warningItem, warningItem
            )
        })
        event.hook.sendMessage(
            """
                ユーザーに警告を送信します。警告の理由をリストから選択してください。
                Send a warning to the user. Select the reason for the warning from the list.
                """.trimIndent()
        ).addActionRow(builder.build()).setEphemeral(true).queue()

        reportContexts[context.messageId.toString()] = context
    }

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val args = event.componentId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // warning_reason_1234567890: args[0] = "warning", args[1] = action, args[2] = processId

        if (args[0] != "warning") // If the component is not a warning component, return
            return

        val processId = args[2] // report embed message id
        val action = args[1]
        val extendInfo = if (args.size > 3) args.copyOfRange(3, args.size) else null

        when (action) {
            "reason" -> {
                val reason = event.values[0]
                val warningMessage = MessageFormat.format(warningTemplate, reason)
                val reportContext = reportContexts[processId] ?: return

                val embedBuilder = EmbedBuilder()
                embedBuilder.setTitle("警告を送信しますか？ / Send a warning?")
                embedBuilder.setDescription(warningMessage)
                embedBuilder.addField(
                    "対象のユーザー / Target user",
                    reportContext.reportTargetUserId,
                    false
                )
                reportContext.reportTargetNoteIds.forEach { noteId ->
                    embedBuilder.addField(
                        "対象の投稿 / Target post",
                        noteId,
                        false
                    )
                }

                event.reply(
                    """
                    以下の内容で警告を送信します。よろしいですか？
                    Send the following warning. Is that okay?
                    
                    ```
                    $warningMessage
                    ```
                """.trimIndent()
                )
                    .addActionRow(
                        Button.danger("warning_confirm_$processId", "はい / Yes"),
                        Button.secondary("warning_cancel_$processId", "いいえ / No")
                    ).setEphemeral(true).queue()

                warningContexts[processId] = ReportWarningContext(
                    reportContext.reportId,
                    reportContext.reportTargetUserId,
                    reportContext.reportTargetNoteIds,
                    warningMessage
                )
                reportContexts.remove(processId)
            }
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val args = event.componentId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // warning_reason_1234567890: args[0] = "warning", args[1] = action, args[2] = processId

        if (args[0] != "warning") // If the component is not a warning component, return
            return

        val processId = args[2] // report embed message id
        val action = args[1]
        val extendInfo = if (args.size > 3) args.copyOfRange(3, args.size) else null

        when (action) {
            "confirm" -> {
                val context = warningContexts[processId] ?: return

                event.deferReply(true).queue() // 処理に3秒以上かかる場合、Discord側でエラーが発生するため、応答を遅らせる

                // TODO: 古い形式への互換性のためのコード
                val ulidRegex = Regex("[0-9A-Z]{26}")
                val searchType =
                    if (ulidRegex.matches(context.reportTargetUsername)) Show.SearchType.ID else Show.SearchType.USERNAME

                val userShow = Show(context.reportTargetUsername, searchType)
                requestManager.addRequest(userShow, object : ApiResponseHandler {
                    override fun onSuccess(response: ApiResponse?) {
                        val user = MAPPER.readValue(response!!.body, FullUser::class.java)

                        if (user == null) {
                            event.hook.sendMessage("ユーザーが見つかりませんでした。 / User not found.").queue()
                            return
                        }

                        val createNote = Create(
                            Visibility.SPECIFIED,
                            arrayOf(user.id),
                            context.warningMessage,
                            null,
                            if (context.reportTargetNoteIds.isNotEmpty()) context.reportTargetNoteIds[0] else null,
                            null,
                            null,
                            null,
                            false,
                            false,
                            false,
                            false
                        )

                        requestManager.addRequest(createNote, object : ApiResponseHandler {
                            override fun onSuccess(response: ApiResponse?) {
                                event.channel.retrieveMessageById(processId).queue { msg: Message ->
                                    if (msg.embeds.isEmpty()) return@queue

                                    // Get Report ID
                                    val reportId = msg.embeds[0].footer!!.text!!.split(":".toRegex())
                                        .dropLastWhile { it.isEmpty() }
                                        .toTypedArray()[1].trim { it <= ' ' }

                                    // Edit Report Embed
                                    val embedBuilder = EmbedBuilder(msg.embeds[0])
                                    embedBuilder.setColor(Color.getHSBColor(0.50f, 0.82f, 0.45f))
                                    embedBuilder.addField("処理 / Process", "警告 / Warning", true)
                                    embedBuilder.addField("処理者 / Processor", event.user.name, true)
                                    embedBuilder.addField(
                                        "処理日時 / Processed Date",
                                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(
                                            Date()
                                        ),
                                        true
                                    )
                                    msg.editMessageEmbeds(embedBuilder.build()).queue()
                                    reportStore.removeReport(event.message.idLong)

                                    // Resolve Report
                                    val resolveAbuseUserReport = ResolveAbuseUserReport(reportId)
                                    requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                                        override fun onSuccess(response: ApiResponse?) {
                                            event.hook.sendMessage(
                                                """
                                                警告を送信し、警告済みとして登録しました。
                                                Warning sent and registered as warned.
                                                """.trimIndent()
                                            ).queue()

                                            // Update User Warned Count
                                            val warningCount = userStore.getWarningCount(user.id!!) + 1
                                            userStore.updateWarningCount(user.id, warningCount)
                                            if (continuousWarningLimit < warningCount) {
                                                event.hook.sendMessage(
                                                    """
                                                    警告回数が規定値を超えています。今後のユーザーの動向に注意してください。
                                                    The number of warnings exceeds the limit. Please pay attention to the future behavior of the user.
                                                    """.trimIndent()
                                                ).queue()
                                            }

                                            // Remove buttons
                                            //event.message.editMessageComponents().queue()
                                            msg.editMessageComponents().queue()

                                            event.hook.sendMessage(
                                                """
                                                同一の投稿に関する通報を自動的にクローズしますか？
                                                Do you want to automatically close the report on the same post?
                                                """.trimIndent()
                                            ).addActionRow(
                                                Button.success(
                                                    "closure_close_$processId",
                                                    "はい / Yes"
                                                ),
                                                Button.secondary(
                                                    "closure_cancel_$processId",
                                                    "いいえ / No"
                                                )
                                            ).setEphemeral(true).queue()
                                        }

                                        override fun onFailure(response: ApiResponse?) {
                                            event.hook.sendMessage(
                                                """
                                                通報のクローズに失敗しました。時間を置いて実行してください。
                                                Report close failed. Please try again later.
                                                """.trimIndent()
                                            ).queue()

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

                            override fun onFailure(response: ApiResponse?) {
                                event.hook.sendMessage(
                                    """
                                    警告の送信に失敗しました。時間を置いて実行してください。
                                    Warning sending failed. Please try again later.
                                    """.trimIndent()
                                ).queue()

                                MisskeyAdminTools.getInstance().moduleLogger.error(
                                    """
                                    An error occurred while sending a warning.
                                    Response Code: {}, Body: {}
                                    """.trimIndent(), response!!.statusCode, response.body
                                )
                            }
                        })
                    }

                    override fun onFailure(response: ApiResponse?) {
                        event.hook.sendMessage(
                            """
                            警告対象のユーザーの検索に失敗しました。時間を置いて実行してください。
                            Failed to search for the warning target user. Please try again later.
                            """.trimIndent()
                        ).queue()

                        MisskeyAdminTools.getInstance().moduleLogger.error(
                            """
                            An error occurred while searching for the warning target user.
                            Response Code: {}, Body: {}
                            """.trimIndent(), response!!.statusCode, response.body
                        )
                    }
                })
            }

            "cancel" -> {
                event.reply("警告の送信をキャンセルしました。 / Warning sending canceled.").setEphemeral(true).queue()
            }
        }
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
