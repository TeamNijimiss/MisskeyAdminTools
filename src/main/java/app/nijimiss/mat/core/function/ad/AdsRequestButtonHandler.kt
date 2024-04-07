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
import app.nijimiss.mat.core.entities.RequestBase
import app.nijimiss.mat.core.function.common.RequestButtonHandler
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.ad.Create
import app.nijimiss.mat.database.AccountsStore
import app.nijimiss.mat.database.AdStore
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.awt.Color
import java.util.*

class AdsRequestButtonHandler(
    private val accountsStore: AccountsStore,
    private val adStore: AdStore,
    private val requestManager: ApiRequestManager,
) : RequestButtonHandler(requestManager) {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val args = event.componentId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // warning_reason_1234567890: args[0] = "warning", args[1] = action, args[2] = processId

        if (args[0] != "ads") // If the component is not a warning component, return
            return

        val processId = args[2] // report embed message id
        val action = args[1]
        val extendInfo = if (args.size > 3) args.copyOfRange(3, args.size) else null

        val context = adStore.getAdRequest(UUID.fromString(processId))
        if (context == null) {
            logger.warn("The specified request does not exist.")
            return
        }

        when (action) {
            "accept" -> {
                acceptRequest(event, context)
            }

            "deny" -> {
                rejectRequest(event, context)
            }
        }
    }

    override fun <T : RequestBase> acceptRequest(event: ButtonInteractionEvent, context: T) {
        if (context !is AdRequest) return

        event.deferEdit().queue()

        val startsAt = System.currentTimeMillis()
        val endsAt = context.endAt ?: (System.currentTimeMillis() + 2678400000)

        val createAd = Create(
            context.linkUrl,
            context.imageUrl,
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

                adStore.approveAd(context.requestId, event.member!!.idLong, startsAt, endsAt)
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

    override fun <T : RequestBase> rejectRequest(event: ButtonInteractionEvent, context: T) {
        super.rejectRequest(event, context)

        adStore.deleteAd(context.requestId)
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
