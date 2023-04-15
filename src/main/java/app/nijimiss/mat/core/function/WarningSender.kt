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
import app.nijimiss.mat.api.misskey.FullUser
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
    private val token: String,
    private val requestManager: ApiRequestManager,
    private val warningTemplate: String,
    private val warningItems: List<String>
) : ListenerAdapter() {
    private val reportContexts: MutableMap<String, ReportContext>
    private val warningContexts: MutableMap<String, ReportWarningContext>

    init {
        reportContexts = HashMap()
        warningContexts = HashMap()

        MisskeyAdminTools.getInstance().jda.addEventListener(this)
    }

    fun sendWarning(context: ReportContext) {
        val builder = StringSelectMenu.create("warning_reason_" + context.messageId)
        builder.setPlaceholder("警告の理由を選択します。 / Select a reason for the warning.")
        warningItems.forEach(Consumer { warningItem: String ->
            builder.addOption(
                warningItem, warningItem
            )
        })
        context.event.reply(
            """
                ユーザーに警告を送信します。警告の理由をリストから選択してください。
                Send a warning to the user. Select the reason for the warning from the list.
                """.trimIndent()
        ).addActionRow(builder.build()).setEphemeral(true).queue()

        reportContexts[context.messageId] = context
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
                    reportContext.reportTargetUsername,
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
                    reportContext.reportTargetUsername,
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

                val userShow = Show(token, context.reportTargetUsername, Show.SearchType.USERNAME)
                requestManager.addRequest(userShow, object : ApiResponseHandler {
                    override fun onSuccess(response: ApiResponse?) {
                        val user = MAPPER.readValue(response!!.body, FullUser::class.java)

                        if (user == null) {
                            event.reply("ユーザーが見つかりませんでした。 / User not found.").setEphemeral(true).queue()
                            return
                        }

                        val createNote = Create(
                            token,
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
                                    embedBuilder.addField("処理者 / Processor", event.user.asTag, true)
                                    embedBuilder.addField(
                                        "処理日時 / Processed Date",
                                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(
                                            Date()
                                        ),
                                        true
                                    )
                                    msg.editMessageEmbeds(embedBuilder.build()).queue()

                                    // Resolve Report
                                    val resolveAbuseUserReport = ResolveAbuseUserReport(token, reportId)
                                    requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                                        override fun onSuccess(response: ApiResponse?) {
                                            event.reply(
                                                """
                                                警告を送信し、警告済みとして登録しました。
                                                Warning sent and registered as warned.
                                                """.trimIndent()
                                            ).setEphemeral(true).queue()

                                            // Remove buttons
                                            //event.message.editMessageComponents(listOf()).queue()
                                            msg.editMessageComponents(listOf()).queue()
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

                            override fun onFailure(response: ApiResponse?) {
                                event.reply(
                                    """
                                    警告の送信に失敗しました。時間を置いて実行してください。
                                    Warning sending failed. Please try again later.
                                    """.trimIndent()
                                ).setEphemeral(true).queue()

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
                        event.reply(
                            """
                            警告対象のユーザーの検索に失敗しました。時間を置いて実行してください。
                            Failed to search for the warning target user. Please try again later.
                            """.trimIndent()
                        ).setEphemeral(true).queue()

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
