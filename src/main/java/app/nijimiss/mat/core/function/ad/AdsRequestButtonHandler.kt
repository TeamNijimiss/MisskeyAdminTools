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
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.ad.Create
import app.nijimiss.mat.database.AccountsStore
import app.nijimiss.mat.database.AdStore
import app.nijimiss.mat.entities.Emoji
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.awt.Color

class AdsRequestButtonHandler(
    private val accountsStore: AccountsStore,
    private val adStore: AdStore,
    private val requestManager: ApiRequestManager,
) : ListenerAdapter() {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val args = event.componentId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // warning_reason_1234567890: args[0] = "warning", args[1] = action, args[2] = processId

        if (args[0] != "ads") // If the component is not a warning component, return
            return

        val processId = args[2] // report embed message id
        val action = args[1]
        val extendInfo = if (args.size > 3) args.copyOfRange(3, args.size) else null

        when (action) {
            "accept" -> {
                val request = adStore.getAdRequest(processId)
                if (request == null) {
                    logger.warn("The specified request does not exist.")
                    return
                }

                event.deferEdit().queue()

                val requesterId = accountsStore.getMisskeyId(request.requesterId)
                val startsAt = System.currentTimeMillis()
                val endsAt = request.endAt ?: (System.currentTimeMillis() + 86400000)

                val createAd = Create(
                    request.linkUrl,
                    request.imageUrl,
                    startsAt,
                    endsAt
                )
                requestManager.addRequest(createAd, object : ApiResponseHandler {
                    override fun onSuccess(response: ApiResponse?) {
                        val embedBuilder = EmbedBuilder(event.message.embeds[0])
                            .setColor(Color.GREEN)
                            .setDescription("The ad has been approved.")
                        event.message.editMessageEmbeds(embedBuilder.build()).queue()
                        event.message.editMessageComponents().queue()

                        val emoji = MAPPER.readValue(
                            response!!.body, Emoji::class.java
                        )

                        adStore.approveAd(processId, event.member!!.idLong, startsAt, endsAt)
                    }

                    override fun onFailure(response: ApiResponse?) {
                        event.hook.sendMessage(
                            """
                            An error occurred while creating the ad.
                            ```
                            ${response?.body}
                            ```
                        """.trimIndent()
                        ).queue()
                    }
                })
            }

            "deny" -> {
                event.deferEdit().queue()

                val embedBuilder = EmbedBuilder(event.message.embeds[0])
                    .setColor(Color.GRAY)
                    .setDescription("The ad has been denied.")
                event.message.editMessageEmbeds(embedBuilder.build()).queue()
                event.message.editMessageComponents().queue()
                adStore.deleteAd(processId)
            }
        }
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
