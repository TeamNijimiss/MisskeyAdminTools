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

import app.nijimiss.mat.core.database.ReportsStore
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.ResolveAbuseUserReport
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class AutoClosure(
    private val token: String,
    private val requestManager: ApiRequestManager,
    private val reportsStore: ReportsStore
) : ListenerAdapter() {

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val args = event.componentId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // closure_close_1234567890: args[0] = "closure", args[1] = action, args[2] = processId

        if (args[0] != "closure") // If the component is not a closure component, return
            return

        val processId = args[2] // report embed message id
        val action = args[1]
        val extendInfo = if (args.size > 3) args.copyOfRange(3, args.size) else null

        event.deferReply(true).queue()

        when (action) {
            "close" -> {
                reportsStore.getReport(processId.toLong())?.let { report ->
                    if (report.reportTargetNoteIds.size > 1) {
                        event.hook.sendMessage(
                            """
                            複数の投稿が指定された通報は自動でクローズすることができません。
                            A report with multiple posts specified cannot be closed automatically.
                            """.trimIndent()
                        ).queue()

                        return
                    }

                    reportsStore.getMessages(report.reportTargetNoteIds[0]).let { messages ->
                        if (messages.isEmpty()) {
                            event.hook.sendMessage(
                                """
                                クローズ可能な通報が見つかりませんでした。
                                No closable reports were found.
                                """.trimIndent()
                            ).queue()

                            return // If the message is not found, return
                        }

                        messages.forEach { id ->
                            if (id != processId.toLong()) // If the message is not the original report embed message, delete it
                                event.channel.retrieveMessageById(id).queue { message ->
                                    reportsStore.getReport(message.idLong)?.let { context ->
                                        val resolveAbuseUserReport = ResolveAbuseUserReport(token, context.reportId)
                                        requestManager.addRequest(resolveAbuseUserReport, object : ApiResponseHandler {
                                            override fun onSuccess(response: ApiResponse?) {
                                                message.delete().queue {
                                                    reportsStore.removeReport(id)
                                                }
                                            }

                                            override fun onFailure(response: ApiResponse?) {
                                                TODO("Not yet implemented")
                                            }
                                        })
                                    }
                                }
                        }
                    }

                    event.hook.sendMessage(
                        """
                        通報をクローズしました。
                        Closed the report.
                        """.trimIndent()
                    ).queue()
                }
            }

            "cancel" -> {
                event.hook.sendMessage(
                    """
                    通報のクローズをキャンセルしました。
                    Cancelled the report closure.
                """.trimIndent()
                ).queue()
            }
        }
    }
}
