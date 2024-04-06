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

package app.nijimiss.mat.core.function.emoji

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.core.entities.RequestBase
import app.nijimiss.mat.core.function.common.RequestButtonHandler
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.admin.emoji.Add
import app.nijimiss.mat.database.AccountsStore
import app.nijimiss.mat.database.EmojiStore
import app.nijimiss.mat.entities.Emoji
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.awt.Color
import java.util.*

class EmojiRequestButtonHandler(
    private val accountsStore: AccountsStore,
    private val emojiStore: EmojiStore,
    private val requestManager: ApiRequestManager,
) : RequestButtonHandler(requestManager) {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val args = event.componentId.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // warning_reason_1234567890: args[0] = "warning", args[1] = action, args[2] = processId

        if (args[0] != "emoji") // If the component is not a warning component, return
            return

        val processId = args[2] // report embed message id
        val action = args[1]
        val extendInfo = if (args.size > 3) args.copyOfRange(3, args.size) else null

        val context = emojiStore.getEmojiRequest(UUID.fromString(processId))
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
        if (context !is EmojiRequest) return

        event.deferEdit().queue()

        val requesterId = accountsStore.getMisskeyId(context.requesterId)
        val addEmoji = Add(
            context.emojiName,
            context.aliases,
            context.imageFileId,
            null,
            context.license,
            context.sensitive,
            context.localOnly,
            requesterId,
            null,
            arrayOf<String>()
        )

        requestManager.addRequest(addEmoji, object : ApiResponseHandler {
            override fun onSuccess(response: ApiResponse?) {
                val embedBuilder = EmbedBuilder(event.message.embeds[0])
                    .setColor(Color.GREEN)
                    .setDescription("Emoji has been added.")
                event.message.editMessageEmbeds(embedBuilder.build()).queue()
                event.message.editMessageComponents().queue()

                val emoji = MAPPER.readValue(
                    response!!.body, Emoji::class.java
                )

                emojiStore.approveEmojiRequest(context.requestId.toString(), event.member!!.idLong, emoji.id!!)
            }

            override fun onFailure(response: ApiResponse?) {
                event.hook.sendMessage(
                    """
                    An error occurred while adding the emoji.
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

        emojiStore.rejectEmojiRequest(context.requestId)
    }

    companion object {
        private val MAPPER = ObjectMapper()
    }
}
