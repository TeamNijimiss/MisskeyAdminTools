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

package app.nijimiss.mat.core.function.common

import app.nijimiss.mat.core.entities.RequestBase
import app.nijimiss.mat.core.requests.ApiRequestManager
import app.nijimiss.mat.core.requests.ApiResponse
import app.nijimiss.mat.core.requests.ApiResponseHandler
import app.nijimiss.mat.core.requests.misskey.endpoints.drive.files.Delete
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.awt.Color

abstract class RequestButtonHandler(private val requestManager: ApiRequestManager) : ListenerAdapter() {

    abstract fun <T : RequestBase> acceptRequest(event: ButtonInteractionEvent, context: T)

    open fun <T : RequestBase> rejectRequest(event: ButtonInteractionEvent, context: T) {
        event.deferEdit().queue()

        val embedBuilder = EmbedBuilder(event.message.embeds[0])
            .setColor(Color.GRAY)
            .setDescription("Request has been denied.")
        event.message.editMessageEmbeds(embedBuilder.build()).queue()
        event.message.editMessageComponents().queue()

        requestManager.addRequest(Delete(context.imageFileId), object : ApiResponseHandler {
            override fun onSuccess(response: ApiResponse?) {
                // Do nothing
            }

            override fun onFailure(response: ApiResponse?) {
                event.hook.sendMessage("Failed to delete the image file.").setEphemeral(true).queue()
            }
        })
    }
}
