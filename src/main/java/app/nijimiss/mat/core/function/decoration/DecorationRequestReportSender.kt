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

package app.nijimiss.mat.core.function.decoration

import app.nijimiss.mat.MisskeyAdminTools
import app.nijimiss.mat.core.entities.RequestBase
import app.nijimiss.mat.core.function.common.RequestHandler
import app.nijimiss.mat.database.AccountsStore
import app.nijimiss.mat.database.DecorationStore
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.buttons.Button
import page.nafuchoco.neobot.api.module.NeoModuleLogger
import java.awt.Color
import java.util.*

class DecorationRequestReportSender(
    private val accountsStore: AccountsStore,
    private val decorationStore: DecorationStore,
    targetReportChannel: Long
) : ListenerAdapter(), RequestHandler {
    private val logger: NeoModuleLogger = MisskeyAdminTools.getInstance().moduleLogger
    private val discordApi: JDA = MisskeyAdminTools.getInstance().jda
    private val targetChannel: TextChannel = discordApi.getTextChannelById(targetReportChannel)
        ?: throw IllegalStateException("The specified channel does not exist.")

    override fun <T : RequestBase> requestCreate(context: T) {
        if (context !is DecorationRequest) return

        decorationStore.insertDecorationRequest(
            DecorationRequest(
                context.requestId,
                context.requesterId,
                context.decorationName,
                context.imageFileId,
                context.imageUrl,
                context.license,
                context.comment,
                context.createAt
            )
        )

        val requestInfo: EmbedBuilder = EmbedBuilder()
            .setTitle("装飾の追加リクエスト / Decoration Request")
            .addField("リクエストID / Request ID", context.requestId.toString(), false)
            .addField(
                "リクエストユーザー / Request User",
                "$context.requesterId (${accountsStore.getMisskeyId(context.requesterId)})", false
            )
            .addField("装飾名 / Decoration Name", context.decorationName, false)
            .addField("ライセンス / License", context.license ?: "None", false)
            .addField("コメント / Comment", context.comment ?: "None", false)
            .setFooter("Request date")
            .setTimestamp(Date().toInstant())
            .setImage(context.imageUrl)
            .setColor(Color.RED)
        targetChannel.sendMessageEmbeds(requestInfo.build()).queue {
            val buttons = listOf(
                Button.primary("deco_accept_$context.requestId", "承認 / Accept"),
                Button.danger("deco_deny_$context.requestId", "拒否 / Deny")
            )
            it.editMessageComponents().setActionRow(buttons).queue()
        }
    }
}
